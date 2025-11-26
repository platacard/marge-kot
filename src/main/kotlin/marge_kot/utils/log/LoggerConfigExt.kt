package marge_kot.utils.log

import io.github.aakira.napier.Napier
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.LoggingConfig
import io.ktor.http.HttpHeaders

fun LoggingConfig.configureLogger() {
  level = LogLevel.BODY
  logger = object : Logger {
    private var lastRequestUrl: String? = null

    override fun log(message: String) {
      val compactMessage = message
        .replace("\n", " ")
        .replace(" +".toRegex(), " ")
        .trim()

      val processedMessage = when {
        compactMessage.startsWith("REQUEST:") -> processRequest(compactMessage)
        compactMessage.startsWith("RESPONSE:") -> processResponse(compactMessage)
        else -> compactMessage
      }

      Napier.i(message = processedMessage)
    }

    private fun processRequest(message: String): String {
      val url = message
        .substringAfter("REQUEST:", "")
        .substringBefore(" METHOD:", "")
        .trim()

      lastRequestUrl = url

      return "REQUEST: $url"
    }

    private fun processResponse(message: String): String {
      val statusCode = message
        .removePrefix("RESPONSE:")
        .trim()
        .substringBefore(' ')
        .trim()

      val body = message
        .substringAfter("BODY:", missingDelimiterValue = "")
        .trim()
        .takeIf { it.isNotEmpty() }
        ?.let { "BODY: $it" }
        ?: "BODY: {}"

      val urlPart = lastRequestUrl?.let { "FROM: $it" } ?: ""
      return "RESPONSE: $statusCode\n$urlPart,\n$body"
    }
  }
  sanitizeHeader { header -> header == HttpHeaders.Authorization }
}