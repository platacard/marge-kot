import kotlinx.coroutines.delay
import marge_kot.repository.Repository
import marge_kot.utils.getLocalProperty
import kotlin.time.Duration.Companion.milliseconds

private const val sleepTimeMs = 30_000L

suspend fun main() {
  val repository = Repository(getLocalProperty("bearer.token"))
  while (true) {
    println("check if any open merge requests assigned to me")
    if (repository.hasAnyAssignedOpenedMergeRequests()) {
      // Do merge logic here
    } else {
      println("sleep for ${sleepTimeMs.milliseconds}")
      delay(sleepTimeMs)
    }
  }
}