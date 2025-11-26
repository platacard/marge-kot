package marge_kot.utils.log

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel

class SimpleAntilog : Antilog() {
  override fun performLog(
    priority: LogLevel,
    tag: String?,
    throwable: Throwable?,
    message: String?
  ) {
    val logMessage = buildString {
      append(priority.name)
      tag?.let { append(" [$it]") }
      message?.let { append(": $it") }
      throwable?.let {
        append("\n")
        append(it.stackTraceToString())
      }
    }
    println(logMessage)
  }
}