package marge_kot.helpers

import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import marge_kot.data.Repository
import marge_kot.data.dto.CannotMergeException

private const val MAX_REBASE_REQUESTS = 10

class RebaseHelper(
  private val repository: Repository,
) {

  suspend fun rebaseIfNeeded(mergeRequestId: Long) {
    var attemptNumber = 0
    var rebaseRequested = false
    var rebaseRequestCount = 0
    val bot = repository.getUserInfo()
    while (true) {
      delay(1500)
      Napier.v("Update merge request")
      val mergeRequest = repository.getMergeRequest(mergeRequestId)
      mergeRequest.checkIfBotStillAssigned(bot)
      if (mergeRequest.rebaseInProgress == true) {
        Napier.v("Rebase is still in progress, let's wait a little")
        continue
      }
      Napier.v("Get last commit from target branch")
      mergeRequest.checkForNoConflicts()
      mergeRequest.checkIfUpdated(
        repository = repository,
        onUpdated = {
          Napier.i("MR $mergeRequestId is already up to date, no rebase needed")
          return
        }
      )
      val targetSha = repository.getBranchInfo(mergeRequest.targetBranch).commit.id
      if (mergeRequest.diffRefs?.baseSha == targetSha) {
        Napier.i("MR $mergeRequestId is already up to date, no rebase needed")
        return
      }
      if (rebaseRequested && mergeRequest.mergeError != null) {
        attemptNumber++
        Napier.w("Rebase attempt $attemptNumber failed for MR $mergeRequestId: ${mergeRequest.mergeError}")
        if (attemptNumber > 3) throw CannotMergeException("Failed to rebase after $attemptNumber attempts")
      }
      if (rebaseRequestCount == MAX_REBASE_REQUESTS) {
        throw CannotMergeException("Merge request is still not up to date after $rebaseRequestCount rebase requests")
      }
      Napier.i("Starting rebase for MR $mergeRequestId against target branch")
      rebaseRequestCount++
      val rebaseResult = repository.rebaseMergeRequest(mergeRequestId)
      rebaseRequested = rebaseResult.mergeError == null
      if (rebaseResult.mergeError != null) {
        attemptNumber++
        Napier.w("Rebase attempt $attemptNumber failed for MR $mergeRequestId: ${rebaseResult.mergeError}")
        if (attemptNumber > 3) throw CannotMergeException("Failed to rebase after $attemptNumber attempts")
        continue
      }
    }
  }
}
