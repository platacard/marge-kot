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
internal const val MERGE_CEILING_ATTEMPTS = 3
private const val RETRY_HINT = "Assign me again to retry"

class MergeHelper(
  private val repository: Repository,
  private val mergeableChecker: MergeRequestMergeableChecker,
  private val rebaseHelper: RebaseHelper,
  private val pipelineChecker: PipelineChecker,
  private val mergeTimeout: Duration,
) {

  suspend fun merge(mergeRequestId: Long) {
    Napier.i("Starting merge process for MR $mergeRequestId")
    val ready = withTimeoutOrNull(mergeTimeout * MERGE_CEILING_ATTEMPTS) {
      attemptUntilReady(mergeRequestId)
    } ?: MergeOutcome.GaveUp(
      "I can't merge this merge request: it was not merged in ${mergeTimeout * MERGE_CEILING_ATTEMPTS}. $RETRY_HINT"
    )
    val outcome = if (ready is MergeOutcome.Ready) mergeNow(mergeRequestId) else ready
    if (outcome is MergeOutcome.GaveUp) {
      Napier.e("Giving up on MR $mergeRequestId: ${outcome.message}")
      giveUp(mergeRequestId, outcome.message)
    }
  }

  private suspend fun attemptUntilReady(mergeRequestId: Long): MergeOutcome {
    var rebaseRestarts = 0
    while (true) {
      val attempt = withTimeoutOrNull(mergeTimeout) {
        classify(mergeRequestId) { runAttempt(mergeRequestId) }
      } ?: return MergeOutcome.GaveUp(
        "I can't merge this merge request: an attempt took longer than $mergeTimeout. $RETRY_HINT"
      )
      if (attempt != MergeOutcome.Restart) return attempt
      rebaseRestarts++
      if (rebaseRestarts > MAX_REBASE_RESTARTS) {
        return MergeOutcome.GaveUp(
          "I can't merge this merge request: it was rebased $MAX_REBASE_RESTARTS times but target branch kept moving. $RETRY_HINT"
        )
      }
      Napier.i("Need rebase again for MR $mergeRequestId, restart $rebaseRestarts of $MAX_REBASE_RESTARTS")
      commentOrLogFailure(mergeRequestId, "Hmm, I guess I should try again")
    }
  }

  private suspend fun runAttempt(mergeRequestId: Long): MergeOutcome {
    mergeableChecker.check(assignCheckIsNeeded = true, mergeRequestId = mergeRequestId)
    rebaseHelper.rebaseIfNeeded(mergeRequestId)
    pipelineChecker.waitForPipeline(mergeRequestId)
    mergeableChecker.check(assignCheckIsNeeded = true, mergeRequestId = mergeRequestId)
    return MergeOutcome.Ready
  }

  private suspend fun mergeNow(mergeRequestId: Long): MergeOutcome {
    val outcome = classify(mergeRequestId) {
      repository.merge(mergeRequestId)
      Napier.i("Successfully merged MR $mergeRequestId")
      MergeOutcome.Merged
    }
    return if (outcome == MergeOutcome.Restart) {
      MergeOutcome.GaveUp("Something bad happened. Please check my logs")
    } else {
      outcome
    }
  }

  private suspend inline fun classify(mergeRequestId: Long, block: () -> MergeOutcome): MergeOutcome {
    return try {
      block()
    } catch (ex: ServerResponseException) {
      Napier.e("Something happened with Gitlab", ex)
      MergeOutcome.GaveUp("Something happened with Gitlab: ${ex.message}")
    } catch (ex: HttpRequestTimeoutException) {
      Napier.e("Request timeout", ex)
      MergeOutcome.GaveUp("Gitlab is not responding: ${ex.message}")
    } catch (_: NeedRebaseException) {
      MergeOutcome.Restart
    } catch (ex: CancellationException) {
      throw ex
    } catch (ex: CannotMergeException) {
      Napier.e("Can't merge MR $mergeRequestId: ${ex.message}", ex)
      MergeOutcome.GaveUp("I can't merge this merge request: ${ex.message}")
    } catch (ex: Throwable) {
      Napier.e("Unhandled exception for MR $mergeRequestId", ex)
      MergeOutcome.GaveUp("Something bad happened. Please check my logs")
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
  data object Ready : MergeOutcome
  data object Restart : MergeOutcome
  data object Merged : MergeOutcome
  data class GaveUp(val message: String) : MergeOutcome
}
