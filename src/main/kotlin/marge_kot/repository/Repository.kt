package marge_kot.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth

class Repository {

  companion object {

    fun createClient(token: String): HttpClient {
      return HttpClient(CIO) {
        defaultRequest {
          url("https://gitlab.diftech.org/api/v4/")
          bearerAuth(token)
        }
      }
    }
  }
}