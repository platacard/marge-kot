package marge_kot.repository

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.get

class Repository {

  companion object {

    fun create() {
      HttpClient()
    }
  }
}