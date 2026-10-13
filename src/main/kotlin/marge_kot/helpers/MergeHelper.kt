package marge_kot.helpers

import io.github.aakira.napier.Napier
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.coroutines.CancellationException
import marge_kot.data.Repository
import marge_kot.data.dto.CannotMergeException
import marge_kot.data.dto.NeedRebaseException

private const val MAX_REBASE_RESTARTS = 5

class MergeHelper(
  private val repository: Repository,
  private val mergeableChecker: MergeRequestMergeableChecker,
  private val rebaseHelper: RebaseHelper,
  private val pipelineChecker: PipelineChecker,
) {

  suspend fun merge(mergeRequestId: Long) {
    Napier.i("Starting merge process for MR $mergeRequestId")
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
        return
      } catch (ex: ServerResponseException) {
        Napier.e("Something happened with Gitlab", ex)
        giveUp(mergeRequestId, "Something happened with Gitlab: ${ex.message}")
        return
      } catch (ex: HttpRequestTimeoutException) {
        Napier.e("Request timeout", ex)
        giveUp(mergeRequestId, "Gitlab is not responding: ${ex.message}")
        return
      } catch (_: NeedRebaseException) {
        rebaseRestarts++
        if (rebaseRestarts > MAX_REBASE_RESTARTS) {
          Napier.e("Giving up on MR $mergeRequestId after $MAX_REBASE_RESTARTS rebase restarts")
          giveUp(
            mergeRequestId,
            "I can't merge this merge request: it was rebased $MAX_REBASE_RESTARTS times but target branch kept moving"
          )
          return
        }
        Napier.i("Need rebase again for MR $mergeRequestId, restart $rebaseRestarts of $MAX_REBASE_RESTARTS")
        commentOrLogFailure(mergeRequestId, "Hmm, I guess I should try again")
        continue
      } catch (ex: CannotMergeException) {
        Napier.e("Can't merge MR $mergeRequestId: ${ex.message}", ex)
        giveUp(mergeRequestId, "I can't merge this merge request: ${ex.message}")
        return
      } catch (ex: Throwable) {
        Napier.e("Unhandled exception for MR $mergeRequestId", ex)
        giveUp(mergeRequestId, "Something bad happened. Please check my logs")
        return
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