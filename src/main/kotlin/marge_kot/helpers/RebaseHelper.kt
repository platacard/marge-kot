package marge_kot.helpers

import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import marge_kot.data.Repository
import marge_kot.data.dto.CannotMergeException

class RebaseHelper(
  private val repository: Repository,
) {

  suspend fun rebaseIfNeeded(mergeRequestId: Long) {
    var attemptNumber = 0
    while (true) {
      delay(1500)
      Napier.v("Update merge request")
      val mergeRequest = repository.getMergeRequest(mergeRequestId)
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
      Napier.i("Starting rebase for MR $mergeRequestId against target branch")
      val rebaseResult = repository.rebaseMergeRequest(mergeRequestId)
      if (rebaseResult.mergeError != null) {
        attemptNumber++
        Napier.w("Rebase attempt $attemptNumber failed for MR $mergeRequestId: ${rebaseResult.mergeError}")
        if (attemptNumber > 3) throw CannotMergeException("Failed to rebase after $attemptNumber attempts")
        continue
      }
    }
  }
}
