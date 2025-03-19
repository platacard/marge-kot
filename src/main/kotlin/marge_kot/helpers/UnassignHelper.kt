package marge_kot.helpers

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import marge_kot.data.Repository

suspend fun unassignBot(
  repository: Repository,
  mergeRequestId: Long,
) {
  coroutineScope {
    val userDeferred = async { repository.getUserInfo() }
    val mergeRequestDeferred = async { repository.getMergeRequest(mergeRequestId) }
    val user = userDeferred.await()
    repository.assignMergeRequestTo(
      mergeRequestId = mergeRequestId,
      newAssignee = mergeRequestDeferred.await()
        .assignees
        .filterNot { it.id == user.id }
    )
  }
}