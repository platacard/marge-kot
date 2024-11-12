package marge_kot.data

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.bearerAuth
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import marge_kot.data.dto.CannotMergeException
import marge_kot.data.dto.NeedRebaseException
import marge_kot.data.dto.ProjectRequest
import marge_kot.data.dto.common.Scope
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

class Repository private constructor(
  private val client: HttpClient
) {

  private val projectId: String = System.getenv("MARGE_KOT_PROJECT_ID") ?: error("Please provide project id")
  private val projectRequest: ProjectRequest = ProjectRequest(projectId)
  private val simpleMergeRequestsRequest = MergeRequests(
    parent = projectRequest,
    state = null,
    scope = null,
  )

  constructor(token: String) : this(createClient(token))

  suspend fun getAssignedOpenedMergeRequests(): List<MergeRequest> {
    try {
      val response = client.get(
        MergeRequests(
          parent = projectRequest,
          scope = Scope.ASSIGNED_TO_ME,
          state = State.OPENED,
        )
      )
      return response.body()
    } catch (e: ServerResponseException) {
      Napier.v(e.message)
      return emptyList()
    }
  }

  suspend fun checkIfMergeRequestApproved(mergeRequestId: Long): Boolean {
    return client.get(
      MergeRequestApprovalsRequest(
        parent = MergeRequestRequest(
          parent = simpleMergeRequestsRequest,
          id = mergeRequestId,
        )
      )
    )
      .body<MergeRequestApprovals>()
      .approved
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

  suspend fun getUserInfo(): User {
    return client.get(UserRequest()).body()
  }

  suspend fun getBranchInfo(branchName: String = "main"): Branch {
    return client.get(
      BranchRequest(
        parent = ProjectRequest(projectId),
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
    var attemptNumber = 1
    while (true) {
      val response = client.put(
        MergeRequestRequest.Merge(
          parent = MergeRequestRequest(
            parent = simpleMergeRequestsRequest,
            id = mergeRequestId,
          )
        )
      ) { expectSuccess = false }
      if (response.status.value == 200) return
      when (response.status.value) {
        405, 409, 422 -> throw NeedRebaseException()
        403, 413 -> attemptNumber++
        else -> throw CannotMergeException(response.status.description)
      }
      if (attemptNumber > 3) {
        Napier.e("Can't merge: ${response.status.description}")
        throw CannotMergeException("I can't merge it even after $attemptNumber attempts. Please check logs")
      }
    }
  }

  suspend fun assignMergeRequestTo(mergeRequestId: Long, newAssignee: LongArray) {
    client.put(
      MergeRequestRequest(
        parent = simpleMergeRequestsRequest,
        assigneeIds = newAssignee.joinToString(","),
        id = mergeRequestId,
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

private fun createClient(token: String): HttpClient {
  return HttpClient(CIO) {
    install(Resources)
    install(ContentNegotiation) {
      json(
        Json {
          ignoreUnknownKeys = true
          coerceInputValues = true
        }
      )
    }
    install(Logging) {
      level = LogLevel.INFO
      logger = object : Logger {
        override fun log(message: String) {
          Napier.i(message = message)
        }
      }
      sanitizeHeader { header -> header == HttpHeaders.Authorization }
    }
    defaultRequest {
      val url = System.getenv("MARGE_KOT_BASE_API") ?: error("Please provide gitlab api base url")
      url(url)
      bearerAuth(token)
    }

    expectSuccess = true
  }
}