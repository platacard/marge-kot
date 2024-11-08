package marge_kot.helpers

import marge_kot.data.dto.merge_request.MergeRequest
import marge_kot.data.dto.merge_request.MergeStatus
import marge_kot.data.repository.Repository
import marge_kot.utils.CannotMergeException

class MergeRequestMergeableChecker(
  private val repository: Repository,
  private val mergeRequestId: Long
) {
  suspend fun check() {
    println("Update merge request")
    val mergeRequest = repository.getMergeRequest(mergeRequestId)
    with(mergeRequest) {
      println("Check if merge request already merged")
      if (mergeStatus == MergeStatus.MERGED) throw CannotMergeException("Merge request was merged already")
      println("Check if merge request is draft")
      if (draft) throw CannotMergeException("I can't merge drafts")
      println("Check if approve count is enough")
      checkIfEnoughApprovers()
      println("Check if there any blocking discussions")
      if (!blockingDiscussionsResolved) throw CannotMergeException("Blocking discussions are not resolved")
      println("Check if bot is still assigned")
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