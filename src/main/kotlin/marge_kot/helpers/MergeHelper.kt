package marge_kot.helpers

import io.github.aakira.napier.Napier
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import marge_kot.data.Repository
import marge_kot.data.dto.CannotMergeException
import marge_kot.data.dto.NeedRebaseException
import kotlin.time.Duration

private const val MAX_REBASE_RESTARTS = 5

class MergeHelper(
  private val repository: Repository,
  private val mergeableChecker: MergeRequestMergeableChecker,
  private val rebaseHelper: RebaseHelper,
  private val pipelineChecker: PipelineChecker,
  private val mergeTimeout: Duration,
) {

  suspend fun merge(mergeRequestId: Long) {
    Napier.i("Starting merge process for MR $mergeRequestId")
    val outcome = withTimeoutOrNull(mergeTimeout) {
      mergeUntilDone(mergeRequestId)
    } ?: MergeOutcome.GaveUp("I can't merge this merge request: it was not merged in $mergeTimeout")
    if (outcome is MergeOutcome.GaveUp) {
      Napier.e("Giving up on MR $mergeRequestId: ${outcome.message}")
      giveUp(mergeRequestId, outcome.message)
    }
  }

  private suspend fun mergeUntilDone(mergeRequestId: Long): MergeOutcome {
    var rebaseRestarts = 0
    while (true) {
      try {
        mergeableChecker.check(
          assignCheckIsNeeded = true,
          mergeRequestId = mergeRequestId
        )
        rebaseHelper.rebaseIfNeeded(mergeRequestId)
        pipelineChecker.waitForPipeline(mergeRequestId)
        mergeableChecker.check(
          assignCheckIsNeeded = true,
          mergeRequestId = mergeRequestId
        )
        repository.merge(mergeRequestId)
        Napier.i("Successfully merged MR $mergeRequestId")
        return MergeOutcome.Merged
      } catch (ex: ServerResponseException) {
        Napier.e("Something happened with Gitlab", ex)
        return MergeOutcome.GaveUp("Something happened with Gitlab: ${ex.message}")
      } catch (ex: HttpRequestTimeoutException) {
        Napier.e("Request timeout", ex)
        return MergeOutcome.GaveUp("Gitlab is not responding: ${ex.message}")
      } catch (_: NeedRebaseException) {
        rebaseRestarts++
        if (rebaseRestarts > MAX_REBASE_RESTARTS) {
          return MergeOutcome.GaveUp(
            "I can't merge this merge request: it was rebased $MAX_REBASE_RESTARTS times but target branch kept moving"
          )
        }
        Napier.i("Need rebase again for MR $mergeRequestId, restart $rebaseRestarts of $MAX_REBASE_RESTARTS")
        commentOrLogFailure(mergeRequestId, "Hmm, I guess I should try again")
        continue
      } catch (ex: CancellationException) {
        throw ex
      } catch (ex: CannotMergeException) {
        Napier.e("Can't merge MR $mergeRequestId: ${ex.message}", ex)
        return MergeOutcome.GaveUp("I can't merge this merge request: ${ex.message}")
      } catch (ex: Throwable) {
        Napier.e("Unhandled exception for MR $mergeRequestId", ex)
        return MergeOutcome.GaveUp("Something bad happened. Please check my logs")
      }
    }
  }

  private suspend fun giveUp(mergeRequestId: Long, message: String) {
    commentOrLogFailure(mergeRequestId, message)
    unassignBot(repository, mergeRequestId)
  }

  private suspend fun commentOrLogFailure(mergeRequestId: Long, message: String) {
    try {
      repository.addCommentToMergeRequest(mergeRequestId = mergeRequestId, message = message)
    } catch (ex: CancellationException) {
      throw ex
    } catch (ex: Throwable) {
      Napier.e("Failed to comment on MR $mergeRequestId", ex)
    }
  }
}

private sealed interface MergeOutcome {
  data object Merged : MergeOutcome
  data class GaveUp(val message: String) : MergeOutcome
}
