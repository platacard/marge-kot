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
        Napier.v("Found merge request with id ${mergeRequest.id} labeled with $label")
        Napier.v("Check if merge request with id ${mergeRequest.id} is mergeable")
        mergeableChecker.check(
          assignCheckIsNeeded = false,
          mergeRequestId = mergeRequest.id
        )
        Napier.v("Check if pipeline in merge request with id ${mergeRequest.id} is not failed")
        checkIfPipelineNotFailed(mergeRequest)

        Napier.v("Merge request with id ${mergeRequest.id} is available to assign to bot")
        mergeRequest
      }.getOrElse { throwable ->
        Napier.d("Merge request with id ${mergeRequest.id} can't be auto-assigned: ${throwable.message ?: throwable.javaClass.simpleName}", throwable)
        null
      }
    }
  }

  private suspend fun checkIfPipelineNotFailed(mergeRequest: MergeRequest) {
    val pipeline = pipelineChecker.getPipelineOrNull(mergeRequest)
    if (pipeline?.status == Pipeline.Status.FAILED) throw CannotMergeException("Pipeline failed")
  }

  private suspend fun List<MergeRequest>.assignAllToKot() {
    if (isEmpty()) return
    val user = repository.getUserInfo()
    Napier.i("Auto-assigning ${size} merge request(s) to bot: ${joinToString { it.id.toString() }}")
    forEach { mergeRequest ->
      repository.assignMergeRequestTo(
        mergeRequestId = mergeRequest.id,
        newAssignee = mergeRequest.assignees.plus(user),
      )
      Napier.i("Assigned MR ${mergeRequest.id} to bot")
    }
  }
}