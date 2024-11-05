package marge_kot.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.get
import io.ktor.client.request.bearerAuth
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import marge_kot.dto.common.Scope
import marge_kot.dto.common.State
import marge_kot.dto.merge_request.MergeRequest
import marge_kot.dto.merge_request.MergeRequestsRequest

class Repository private constructor(
  private val client: HttpClient
) {

  constructor(token: String) : this(createClient(token))

  suspend fun hasAnyAssignedOpenedMergeRequests(): Boolean {
    try {
      val response = client.get(
        MergeRequestsRequest(
          scope = Scope.ASSIGNED_TO_ME,
          state = State.OPENED,
        )
      )
      return response.body<List<MergeRequest>>().isNotEmpty()
    } catch (e: ServerResponseException) {
      println(e.message)
      return false
    }
  }
}

private fun createClient(token: String): HttpClient {
  return HttpClient(CIO) {
    install(Resources)
    install(ContentNegotiation) {
      json(Json {
        ignoreUnknownKeys = true
      })
    }
    defaultRequest {
      url("https://gitlab.diftech.org/api/v4/")
      bearerAuth(token)
    }

    expectSuccess = true
  }
}
