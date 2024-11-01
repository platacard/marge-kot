package marge_kot.executor

import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.delay
import marge_kot.repository.Repository
import marge_kot.utils.getLocalProperty

object Executor {

  suspend fun runBot() {
    val client = Repository.createClient(getLocalProperty("bearer.token"))
    while (true) {
      val request = client.get("projects")
      println("body = ${request.body<String>()}")
      delay(3000)
      println("oops I did it again")
    }
  }
}