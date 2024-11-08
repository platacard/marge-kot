import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import marge_kot.data.Repository
import marge_kot.helpers.MergeHelper
import marge_kot.utils.getLocalProperty
import kotlin.time.Duration.Companion.milliseconds

private const val sleepTimeMs = 30_000L

suspend fun main() {
  val repository = Repository(
    // TODO: move to input params
    getLocalProperty("bearer.token")
  )
  while (true) {
    Napier.v("check if any open merge requests assigned to me")
    val assignedOpenedMergeRequests = repository.getAssignedOpenedMergeRequests()
    if (assignedOpenedMergeRequests.isNotEmpty()) {
      val mergeRequest = assignedOpenedMergeRequests.last()
      val helper = MergeHelper(
        repository = repository,
      )
      Napier.v("Merge request with id ${mergeRequest.id} found")
      helper.merge(mergeRequest)
    } else {
      Napier.v("sleep for ${sleepTimeMs.milliseconds}")
      delay(sleepTimeMs)
    }
  }
}