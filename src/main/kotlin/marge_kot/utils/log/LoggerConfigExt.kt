package marge_kot.utils.log

import io.github.aakira.napier.Napier
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.LoggingConfig
import io.ktor.http.HttpHeaders

fun LoggingConfig.configureLogger() {
  level = LogLevel.INFO
  logger = object : Logger {
    override fun log(message: String) {
      val compactMessage = message
        .replace("\n", " ")
        .replace(" +".toRegex(), " ")
      Napier.i(message = compactMessage)
    }
  }
  sanitizeHeader { header -> header == HttpHeaders.Authorization }
}