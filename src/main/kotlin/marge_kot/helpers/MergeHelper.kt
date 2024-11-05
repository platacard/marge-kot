package marge_kot.helpers

import marge_kot.dto.merge_request.MergeRequest
import marge_kot.dto.merge_request.MergeStatus
import marge_kot.repository.Repository

class MergeHelper(
  val mergeRequest: MergeRequest,
  val repository: Repository,
) {

  suspend fun merge() {
    checkIfMergeRequestCanBeMerged()
  }

  private suspend fun checkIfMergeRequestCanBeMerged() {
    with(mergeRequest) {
      if (mergeStatus == MergeStatus.MERGED) throw CannotMerge("Merge request was merged already")
      if (draft) throw CannotMerge("I can't merge drafts")
      checkIfEnoughApprovers()
      if (!blockingDiscussionsResolved) throw CannotMerge("Blocking discussions are not resolved")
      checkIfBotStillAssigned()
    }
  }

  private suspend fun MergeRequest.checkIfEnoughApprovers() {
    if (!repository.checkIfMergeRequestApproved(id)) throw CannotMerge("Insufficient approvers")
  }

  private suspend fun MergeRequest.checkIfBotStillAssigned() {
    val user = repository.getUserInfo()
    if (!assignees.contains(user)) throw CannotMerge("I was unassigned")
  }
}