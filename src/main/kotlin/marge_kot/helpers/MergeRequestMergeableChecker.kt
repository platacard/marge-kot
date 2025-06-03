package marge_kot.helpers

import io.github.aakira.napier.Napier
import marge_kot.data.Repository
import marge_kot.data.dto.CannotMergeException
import marge_kot.data.dto.merge_request.MergeRequest
import marge_kot.data.dto.merge_request.MergeStatus

class MergeRequestMergeableChecker(
  private val repository: Repository,
) {

  suspend fun check(assignCheckIsNeeded: Boolean, mergeRequestId: Long) {
    val mergeRequest = repository.getMergeRequest(mergeRequestId)
    with(mergeRequest) {
      Napier.v("Check if merge request already merged")
      if (mergeStatus == MergeStatus.MERGED) throw CannotMergeException("Merge request was merged already")

      Napier.v("Check if merge request is draft")
      if (draft) throw CannotMergeException("I can't merge drafts")

      Napier.v("Check if approve count is enough")
      if (!repository.checkIfMergeRequestApproved(id)) throw CannotMergeException("Insufficient approves")

      Napier.v("Check if there any blocking discussions")
      if (!blockingDiscussionsResolved) throw CannotMergeException("Blocking discussions are not resolved")

      checkForNoConflicts()
      if (assignCheckIsNeeded) {
        Napier.v("Check if bot is still assigned")
        checkIfBotStillAssigned()
      }
    }
  }

  private suspend fun MergeRequest.checkIfBotStillAssigned() {
    val user = repository.getUserInfo()
    if (!assignees.contains(user)) throw CannotMergeException("I was unassigned")
  }
}

fun MergeRequest.checkForNoConflicts() {
  Napier.v("Check if has any conflicts")
  if (hasConflicts == true) throw CannotMergeException("You have conflicts with target branch")
}