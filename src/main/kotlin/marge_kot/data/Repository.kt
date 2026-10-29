package marge_kot.data

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingConfig
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.bearerAuth
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import marge_kot.data.dto.Project
import marge_kot.data.dto.ProjectRequest
import marge_kot.data.dto.common.OrderBy
import marge_kot.data.dto.common.Scope
import marge_kot.data.dto.common.Sort
import marge_kot.data.dto.common.State
import marge_kot.data.dto.git.Branch
import marge_kot.data.dto.git.BranchRequest
import marge_kot.data.dto.merge_request.MergeRequest
import marge_kot.data.dto.merge_request.MergeRequestApprovals
import marge_kot.data.dto.merge_request.MergeRequestApprovalsRequest
import marge_kot.data.dto.merge_request.MergeRequestRequest
import marge_kot.data.dto.merge_request.MergeRequests
import marge_kot.data.dto.merge_request.RebaseRequest
import marge_kot.data.dto.merge_request.RebaseResult
import marge_kot.data.dto.pipeline.Pipeline
import marge_kot.data.dto.pipeline.PipelineRequest
import marge_kot.data.dto.user.User
import marge_kot.data.dto.user.UserRequest
import marge_kot.utils.log.configureLogger

private const val MAX_PER_PAGE = 100

class Repository(
  projectId: String = System.getenv("MARGE_KOT_PROJECT_ID") ?: error("Please provide project id"),
  private val client: HttpClient = createClient(),
) {

  private val projectRequest: ProjectRequest = ProjectRequest(projectId)
  private val simpleMergeRequestsRequest = MergeRequests(
    parent = projectRequest,
    state = null,
    scope = null,
  )

  suspend fun getOpenedMergeRequests(
    targetBranch: String,
    scope: Scope,
    label: String? = null,
  ): List<MergeRequest> {
    try {
      val mergeRequests = mutableListOf<MergeRequest>()
      var page: Int? = 1
      while (page != null) {
        val response = client.get(
          MergeRequests(
            parent = projectRequest,
            scope = scope,
            state = State.OPENED,
            targetBranch = targetBranch,
            orderBy = OrderBy.UPDATED_AT,
            sort = Sort.ASCENDING,
            label = label,
            perPage = MAX_PER_PAGE,
            page = page,
          )
        )
        mergeRequests += response.body<List<MergeRequest>>()
        page = response.headers["X-Next-Page"]?.toIntOrNull()
      }
      return mergeRequests
    } catch (e: Throwable) {
      Napier.e("Failed to fetch merge requests for branch $targetBranch, scope $scope, label $label", e)
      return emptyList()
    }
  }

  suspend fun checkIfMergeRequestApproved(mergeRequestId: Long): Boolean {
    val result = client.get(
      MergeRequestApprovalsRequest(
        parent = MergeRequestRequest(
          parent = simpleMergeRequestsRequest,
          id = mergeRequestId,
        )
      )
    )
      .body<MergeRequestApprovals>()

    // Sometimes gitlab returns `approved = false` while `approvalsLeft == 0`
    return result.approved || result.approvalsLeft == 0
  }

  suspend fun getMergeRequest(id: Long): MergeRequest {
    return client.get(
      MergeRequestRequest(
        parent = simpleMergeRequestsRequest,
        includeRebaseInProgress = true,
        id = id,
      )
    ).body()
  }

  suspend fun getProject(): Project {
    return client.get(projectRequest).body()
  }

  suspend fun getUserInfo(): User {
    return client.get(UserRequest()).body()
  }

  suspend fun getBranchInfo(branchName: String = "main"): Branch {
    return client.get(
      BranchRequest(
        parent = projectRequest,
        name = branchName,
      )
    ).body()
  }

  suspend fun rebaseMergeRequest(mergeRequestId: Long): RebaseResult {
    return client.put(
      RebaseRequest(
        MergeRequestRequest(
          parent = simpleMergeRequestsRequest,
          id = mergeRequestId,
        )
      )
    ).body()
  }

  suspend fun getPipeline(pipelineId: Long): Pipeline {
    return client.get(
      PipelineRequest(
        parent = projectRequest,
        id = pipelineId
      )
    ).body()
  }

  suspend fun merge(mergeRequestId: Long) {
    client.put(
      MergeRequestRequest.Merge(
        parent = MergeRequestRequest(
          parent = simpleMergeRequestsRequest,
          id = mergeRequestId,
        )
      )
    )
  }

  suspend fun assignMergeRequestTo(mergeRequestId: Long, newAssignee: List<User>) {
    client.put(
      MergeRequestRequest(
        parent = simpleMergeRequestsRequest,
        assigneeIds = newAssignee.map(User::id).joinToString(","),
        id = mergeRequestId,
      )
    )
  }

  suspend fun setLabelsToMergeRequest(mergeRequestId: Long, newLabels: List<String>) {
    client.put(
      MergeRequestRequest(
        parent = simpleMergeRequestsRequest,
        id = mergeRequestId,
        labels = newLabels.joinToString(","),
      )
    )
  }

  suspend fun addCommentToMergeRequest(mergeRequestId: Long, message: String) {
    client.post(
      MergeRequestRequest.Notes(
        parent = MergeRequestRequest(
          parent = simpleMergeRequestsRequest,
          id = mergeRequestId,
        ),
        body = message,
      )
    )
  }
}

internal fun createClient(
  engine: HttpClientEngine = CIO.create(),
  baseUrl: String = System.getenv("MARGE_KOT_BASE_API") ?: error("Please provide gitlab api base url"),
  authToken: String = System.getenv("MARGE_KOT_AUTH_TOKEN") ?: error("Please provide auth token for Gitlab"),
): HttpClient {
  return HttpClient(engine) {
    install(Resources)
    install(ContentNegotiation) {
      json(
        Json {
          ignoreUnknownKeys = true
          coerceInputValues = true
        }
      )
    }
    val debuggable = System.getenv("MARGE_KOT_ADD_REQUEST_LOGS").toBoolean()
    if (debuggable) {
      install(plugin = Logging, configure = LoggingConfig::configureLogger)
    }
    install(HttpRequestRetry) {
      retryOnServerErrors(maxRetries = 5)
      retryOnException(maxRetries = 5, retryOnTimeout = true)
      exponentialDelay()
    }

    defaultRequest {
      url(baseUrl)
      bearerAuth(authToken)
    }

    expectSuccess = true
  }
}
