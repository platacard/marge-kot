package marge_kot.helpers

import kotlinx.coroutines.delay
import marge_kot.repository.Repository

class RebaseHelper(
  private val repository: Repository,
  private val mergeRequestId: Long
) {

  suspend fun rebaseIfNeeded() {
    var attemptNumber = 0
    while (true) {
      delay(3000)
      val mergeRequest = repository.getMergeRequest(mergeRequestId)
      if (mergeRequest.rebaseInProgress) continue
      val targetSha = repository.getBranchInfo(mergeRequest.targetBranch).commit.id
      if (mergeRequest.diffRefs.baseSha == targetSha) return
      val rebaseResult = repository.rebaseMergeRequest(mergeRequestId)
      if (rebaseResult.mergeError != null) {
        attemptNumber++
        if (attemptNumber > 3) throw RebaseError("Failed to rebase after $attemptNumber attempts")
        continue
      }
      if (rebaseResult.rebaseInProgress) continue
    }
  }
}

class RebaseError(override val message: String): Throwable(message)