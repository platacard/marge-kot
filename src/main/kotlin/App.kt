import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import marge_kot.data.Repository
import marge_kot.helpers.MergeHelper
import kotlin.time.Duration.Companion.milliseconds

private const val sleepTimeMs = 30_000L

suspend fun main() {
  initLogger()
  val bearer = System.getenv("MARGE_KOT_AUTH_TOKEN") ?: error("Please provide auth token for Gitlab")
  val repository = Repository(bearer)
  while (true) {
    Napier.v("check if any open merge requests assigned to me")
    val assignedOpenedMergeRequests = repository.getAssignedOpenedMergeRequests()
    if (assignedOpenedMergeRequests.isNotEmpty()) {
      val mergeRequest = assignedOpenedMergeRequests.last()
      val helper = MergeHelper(repository)
      Napier.v("Merge request with id ${mergeRequest.id} found")
      helper.merge(mergeRequest)
    } else {
      Napier.v("sleep for ${sleepTimeMs.milliseconds}")
      delay(sleepTimeMs)
    }
  }
}

private fun initLogger() {
  Napier.base(DebugAntilog())
}