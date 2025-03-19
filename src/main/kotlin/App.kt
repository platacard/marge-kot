import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import marge_kot.data.Repository
import marge_kot.data.dto.common.Scope
import marge_kot.helpers.LabelHandler
import marge_kot.helpers.MergeHelper
import marge_kot.helpers.MergeRequestMergeableChecker
import marge_kot.utils.log.SimpleAntilog
import kotlin.time.Duration.Companion.milliseconds

private const val sleepTimeMs = 30_000L

suspend fun main() {
  initLogger()

  val repository = Repository()
  val mergeableChecker = MergeRequestMergeableChecker(repository)
  val labelHandler = LabelHandler(repository, mergeableChecker)

  while (true) {
    Napier.v("check if any open merge requests assigned to me")
    val targetBranch = System.getenv("MARGE_KOT_TARGET_BRANCH") ?: error("Please provide target branch")
    labelHandler.processLabeledMergeRequests(targetBranch)
    val assignedOpenedMergeRequests = repository.getOpenedMergeRequests(
      scope = Scope.ASSIGNED_TO_ME,
      targetBranch = targetBranch
    )
    if (assignedOpenedMergeRequests.isNotEmpty()) {
      val mergeRequest = assignedOpenedMergeRequests.first()
      val helper = MergeHelper(
        repository = repository,
        mergeableChecker = mergeableChecker
      )
      Napier.v("Merge request with id ${mergeRequest.id} found")
      helper.merge(mergeRequest)
    } else {
      Napier.v("sleep for ${sleepTimeMs.milliseconds}")
      delay(sleepTimeMs)
    }
  }
}

private fun initLogger() {
  Napier.base(SimpleAntilog())
}