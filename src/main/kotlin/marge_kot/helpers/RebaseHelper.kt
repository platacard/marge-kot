package marge_kot.helpers

import kotlinx.coroutines.delay
import marge_kot.data.repository.Repository

class RebaseHelper(
  private val repository: Repository,
  private val mergeRequestId: Long
) {

  suspend fun rebaseIfNeeded() {
    var attemptNumber = 0
    while (true) {
      delay(1500)
      println("Update merge request")
      val mergeRequest = repository.getMergeRequest(mergeRequestId)
      if (mergeRequest.rebaseInProgress == true) {
        println("Rebase is still in progress, let's wait a little")
        continue
      }
      println("Get last commit from target branch")
      val targetSha = repository.getBranchInfo(mergeRequest.targetBranch).commit.id
      if (mergeRequest.diffRefs?.baseSha == targetSha) {
        println("Rebase done")
        return
      }
      println("Rebase against target")
      val rebaseResult = repository.rebaseMergeRequest(mergeRequestId)
      if (rebaseResult.mergeError != null) {
        attemptNumber++
        if (attemptNumber > 3) throw RebaseErrorException("Failed to rebase after $attemptNumber attempts")
        continue
      }
    }
  }
}

class RebaseErrorException(override val message: String): Throwable(message)
