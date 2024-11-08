package marge_kot.helpers

import io.github.aakira.napier.Napier
import marge_kot.data.Repository
import marge_kot.data.dto.merge_request.MergeRequest
import marge_kot.data.dto.merge_request.MergeStatus
import marge_kot.utils.CannotMergeException

class MergeRequestMergeableChecker(
  private val repository: Repository,
  private val mergeRequestId: Long
) {
  suspend fun check() {
    Napier.v("Update merge request")
    val mergeRequest = repository.getMergeRequest(mergeRequestId)
    with(mergeRequest) {
      Napier.v("Check if merge request already merged")
      if (mergeStatus == MergeStatus.MERGED) throw CannotMergeException("Merge request was merged already")
      Napier.v("Check if merge request is draft")
      if (draft) throw CannotMergeException("I can't merge drafts")
      Napier.v("Check if approve count is enough")
      checkIfEnoughApprovers()
      Napier.v("Check if there any blocking discussions")
      if (!blockingDiscussionsResolved) throw CannotMergeException("Blocking discussions are not resolved")
      Napier.v("Check if bot is still assigned")
      checkIfBotStillAssigned()
    }
  }

  private suspend fun MergeRequest.checkIfEnoughApprovers() {
    if (!repository.checkIfMergeRequestApproved(id)) throw CannotMergeException("Insufficient approvers")
  }

  private suspend fun MergeRequest.checkIfBotStillAssigned() {
    val user = repository.getUserInfo()
    if (!assignees.contains(user)) throw CannotMergeException("I was unassigned")
  }
}