import kotlinx.coroutines.delay
import marge_kot.helpers.MergeHelper
import marge_kot.repository.Repository
import marge_kot.utils.getLocalProperty
import kotlin.time.Duration.Companion.milliseconds

private const val sleepTimeMs = 30_000L

suspend fun main() {
  val repository = Repository(
    // TODO: move to input params
    getLocalProperty("bearer.token")
  )
  while (true) {
    println("check if any open merge requests assigned to me")
    val assignedOpenedMergeRequests = repository.getAssignedOpenedMergeRequests()
    if (assignedOpenedMergeRequests.isNotEmpty()) {
      val helper = MergeHelper(
        mergeRequest = assignedOpenedMergeRequests.last(),
        repository = repository,
      )
      helper.merge()
    } else {
      println("sleep for ${sleepTimeMs.milliseconds}")
      delay(sleepTimeMs)
    }
  }
}