package marge_kot.helpers

import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import marge_kot.data.Repository
import marge_kot.data.dto.CannotMergeException

class RebaseHelper(
  private val repository: Repository,
  private val mergeRequestId: Long
) {

  suspend fun rebaseIfNeeded() {
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
      val targetSha = repository.getBranchInfo(mergeRequest.targetBranch).commit.id
      if (mergeRequest.diffRefs?.baseSha == targetSha) {
        Napier.v("Rebase done")
        return
      }
      Napier.v("Rebase against target")
      val rebaseResult = repository.rebaseMergeRequest(mergeRequestId)
      if (rebaseResult.mergeError != null) {
        attemptNumber++
        if (attemptNumber > 3) throw CannotMergeException("Failed to rebase after $attemptNumber attempts")
        continue
      }
    }
  }
}
