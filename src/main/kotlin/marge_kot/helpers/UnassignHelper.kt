package marge_kot.helpers

import io.github.aakira.napier.Napier
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import marge_kot.data.Repository

suspend fun unassignBot(
  repository: Repository,
  mergeRequestId: Long,
  autoMergeLabel: String? = System.getenv("MARGE_KOT_AUTO_MERGE_LABEL"),
) {
  coroutineScope {
    val userDeferred = async { repository.getUserInfo() }
    val mergeRequestDeferred = async { repository.getMergeRequest(mergeRequestId) }
    val user = userDeferred.await()
    val mergeRequest = mergeRequestDeferred.await()

    repository.assignMergeRequestTo(
      mergeRequestId = mergeRequestId,
      newAssignee = mergeRequest.assignees.filterNot { it.id == user.id },
    )

    if (autoMergeLabel != null && mergeRequest.labels.contains(autoMergeLabel)) {
      Napier.i("Removing '$autoMergeLabel' label from MR $mergeRequestId after unassigning bot")
      repository.setLabelsToMergeRequest(
        mergeRequestId = mergeRequestId,
        newLabels = mergeRequest.labels.filterNot { it == autoMergeLabel },
      )
    }
  }
}
