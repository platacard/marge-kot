import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import marge_kot.data.Repository
import marge_kot.data.dto.common.Scope
import marge_kot.di.appModule
import marge_kot.helpers.LabelHandler
import marge_kot.helpers.MergeHelper
import marge_kot.utils.log.SimpleAntilog
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.time.Duration.Companion.milliseconds

private const val sleepTimeMs = 30_000L

suspend fun main() {
  initLogger()
  startKoin {
    modules(appModule)
  }

  try {
    val app = App()
    app.main()
  } finally {
    stopKoin()
  }
}

class App : KoinComponent {
  private val repository: Repository by inject()
  private val labelHandler: LabelHandler by inject()
  private val mergeHelper: MergeHelper by inject()

  suspend fun main() {
    while (true) {
      val targetBranch = System.getenv("MARGE_KOT_TARGET_BRANCH") ?: error("Please provide target branch")
      Napier.v("check if any open merge requests assigned to me for target branch $targetBranch")
      labelHandler.processLabeledMergeRequests(targetBranch)
      val assignedOpenedMergeRequests = repository.getOpenedMergeRequests(
        scope = Scope.ASSIGNED_TO_ME,
        targetBranch = targetBranch
      )
      if (assignedOpenedMergeRequests.isNotEmpty()) {
        val mergeRequest = assignedOpenedMergeRequests.first()
        Napier.v("Merge request with id ${mergeRequest.id} found")
        mergeHelper.merge(mergeRequest)
      } else {
        Napier.v("sleep for ${sleepTimeMs.milliseconds}")
        delay(sleepTimeMs)
      }
    }
  }
}

private fun initLogger() {
  Napier.base(SimpleAntilog())
}