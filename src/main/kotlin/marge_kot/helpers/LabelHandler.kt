package marge_kot.helpers

import io.github.aakira.napier.Napier
import marge_kot.data.Repository
import marge_kot.data.dto.common.Scope
import marge_kot.data.dto.merge_request.MergeRequest

class LabelHandler(
  private val repository: Repository,
  private val mergeableChecker: MergeRequestMergeableChecker,
) {

  private val label: String? = System.getenv("MARGE_KOT_AUTO_MERGE_LABEL")

  suspend fun processLabeledMergeRequests(targetBranch: String) {
    if (label == null) return

    val mergeRequestsAvailableToAssign = getMergeRequestsToAssign(targetBranch)
    mergeRequestsAvailableToAssign.assignAllToKotAndRemoveLabel()
  }

  private suspend fun getMergeRequestsToAssign(targetBranch: String): List<MergeRequest> {
    val mergeRequests = repository.getOpenedMergeRequests(
      targetBranch = targetBranch,
      scope = Scope.ALL,
      label = label,
    )
    return mergeRequests.mapNotNull { mergeRequest ->
      runCatching {
        mergeableChecker.check(
          assignCheckIsNeeded = false,
          mergeRequestId = mergeRequest.id
        )
        mergeRequest
      }.getOrElse { throwable ->
        Napier.v("Merge request with id ${mergeRequest.id} can't be merged: ${throwable.message}")
        null
      }
    }
  }

  private suspend fun List<MergeRequest>.assignAllToKotAndRemoveLabel() {
    val user = repository.getUserInfo()
    forEach { mergeRequest ->
      repository.assignMergeRequestTo(
        mergeRequestId = mergeRequest.id,
        newAssignee = mergeRequest.assignees.plus(user),
      )
      repository.setLabelsToMergeRequest(
        mergeRequestId = mergeRequest.id,
        newLabels = mergeRequest.labels.filterNot { it == label },
      )
    }
  }
}