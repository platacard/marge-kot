package marge_kot.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.bearerAuth
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
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

  // TODO: move to input params
  private val projectId: String = "20"
  private val projectRequest: ProjectRequest = ProjectRequest(projectId)

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
      println(e.message)
      return emptyList()
    }
  }

  suspend fun checkIfMergeRequestApproved(mergeRequestId: Long): Boolean {
    return client.get(
      MergeRequestApprovalsRequest(
        parent = MergeRequestRequest(
          parent = MergeRequests(
            scope = null,
            state = null,
            parent = projectRequest
          ),
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
        parent = MergeRequests(
          scope = null,
          state = null,
          parent = ProjectRequest(projectId),
        ),
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
          MergeRequests(
            scope = null,
            state = null,
            parent = projectRequest,
          ),
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
    defaultRequest {
      // TODO: move to input params
      url("https://gitlab.diftech.org/api/v4/")
      bearerAuth(token)
    }

    expectSuccess = true
  }
}
