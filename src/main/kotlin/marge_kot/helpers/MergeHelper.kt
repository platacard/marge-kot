package marge_kot.helpers

import io.github.aakira.napier.Napier
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import marge_kot.data.Repository
import marge_kot.data.dto.CannotMergeException
import marge_kot.data.dto.NeedRebaseException

class MergeHelper(
  private val repository: Repository,
  private val mergeableChecker: MergeRequestMergeableChecker,
  private val rebaseHelper: RebaseHelper,
  private val pipelineChecker: PipelineChecker,
) {

  suspend fun merge(mergeRequestId: Long) {
    Napier.i("Starting merge process for MR $mergeRequestId")
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
        repository.addCommentToMergeRequest(
          mergeRequestId = mergeRequestId,
          message = "Something happened with Gitlab: ${ex.message}"
        )
        unassignBot(repository, mergeRequestId)
        return
      } catch (ex: HttpRequestTimeoutException) {
        Napier.e("Request timeout", ex)
        repository.addCommentToMergeRequest(
          mergeRequestId = mergeRequestId,
          message = "Gitlab is not responding: ${ex.message}"
        )
        unassignBot(repository, mergeRequestId)
        return
      } catch (_: NeedRebaseException) {
        Napier.i("Need rebase again for MR $mergeRequestId")
        repository.addCommentToMergeRequest(
          mergeRequestId = mergeRequestId,
          message = "Hmm, I guess I should try again"
        )
        continue
      } catch (ex: CannotMergeException) {
        Napier.e("Can't merge MR $mergeRequestId: ${ex.message}", ex)
        repository.addCommentToMergeRequest(
          mergeRequestId = mergeRequestId,
          message = "I can't merge this merge request: ${ex.message}"
        )
        unassignBot(repository, mergeRequestId)
        return
      } catch (ex: Throwable) {
        Napier.e("Unhandled exception for MR $mergeRequestId", ex)
        repository.addCommentToMergeRequest(
          mergeRequestId = mergeRequestId,
          message = "Something bad happened. Please check my logs"
        )
        unassignBot(repository, mergeRequestId)
        return
      }
    }
  }
}