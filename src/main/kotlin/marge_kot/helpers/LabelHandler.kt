package marge_kot.helpers

import io.github.aakira.napier.Napier
import marge_kot.data.Repository
import marge_kot.data.dto.CannotMergeException
import marge_kot.data.dto.common.Scope
import marge_kot.data.dto.merge_request.MergeRequest
import marge_kot.data.dto.pipeline.Pipeline

class LabelHandler(
  private val repository: Repository,
  private val mergeableChecker: MergeRequestMergeableChecker,
  private val pipelineChecker: PipelineChecker,
  private val label: String? = System.getenv("MARGE_KOT_AUTO_MERGE_LABEL"),
) {

  suspend fun processLabeledMergeRequests(targetBranch: String) {
    if (label == null) return

    val mergeRequestsAvailableToAssign = getMergeRequestsToAssign(targetBranch)
    if (mergeRequestsAvailableToAssign.isEmpty()) return

    mergeRequestsAvailableToAssign.assignAllToKot()
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
        checkIfPipelineNotFailed(mergeRequest)

        mergeRequest
      }.getOrElse { throwable ->
        Napier.v("Merge request with id ${mergeRequest.id} can't be merged: ${throwable.message ?: throwable.javaClass.simpleName}")
        null
      }
    }
  }

  private suspend fun checkIfPipelineNotFailed(mergeRequest: MergeRequest) {
    val pipeline = pipelineChecker.getPipelineOrNull(mergeRequest)
    if (pipeline?.status == Pipeline.Status.FAILED) throw CannotMergeException("Pipeline failed")
  }

  private suspend fun List<MergeRequest>.assignAllToKot() {
    val user = repository.getUserInfo()
    forEach { mergeRequest ->
      repository.assignMergeRequestTo(
        mergeRequestId = mergeRequest.id,
        newAssignee = mergeRequest.assignees.plus(user),
      )
    }
  }
}