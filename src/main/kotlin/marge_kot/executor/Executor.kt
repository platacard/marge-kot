package marge_kot.executor

import io.ktor.client.HttpClient
import kotlinx.coroutines.delay

object Executor {

  suspend fun runBot() {
    HttpClient()
    while (true) {
      delay(3000)
      println("pupupu")
    }
  }
}