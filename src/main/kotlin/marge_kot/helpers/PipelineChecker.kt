package marge_kot.helpers

import io.github.aakira.napier.Napier
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import marge_kot.data.Repository
import marge_kot.data.dto.CannotMergeException
import marge_kot.data.dto.NeedRebaseException
import marge_kot.data.dto.merge_request.MergeRequest
import marge_kot.data.dto.pipeline.Pipeline

class PipelineChecker(
  private val repository: Repository,
  private val mergeableChecker: MergeRequestMergeableChecker,
) {

  suspend fun waitForPipeline(mergeRequestId: Long) {
    Napier.i("Waiting for pipeline to complete for MR $mergeRequestId")
    coroutineScope {
      while (true) {
        listOf(
          launch { delay(3000) },
          launch { mergeableChecker.check(assignCheckIsNeeded = true, mergeRequestId = mergeRequestId) }
        ).joinAll()
        Napier.v("Fetch merge request")
        val mergeRequest = repository.getMergeRequest(mergeRequestId)
        mergeRequest.checkIfUpdated(
          repository = repository,
          onOutdated = { throw NeedRebaseException() }
        )
        val pipelineFinished = checkIfPipelineFinished(mergeRequest)
        if (pipelineFinished) {
          Napier.i("Pipeline finished successfully for MR $mergeRequestId")
          return@coroutineScope
        }
      }
    }
  }

  suspend fun checkIfPipelineFinished(mergeRequest: MergeRequest): Boolean {
    Napier.v("Get pipeline for current merge request")
    val pipeline = getPipelineOrNull(mergeRequest) ?: run {
      Napier.v("No pipeline yet for MR ${mergeRequest.id}")
      return false
    }
    Napier.v("Check if pipeline is actual")
    if (mergeRequest.diffRefs?.headSha != pipeline.sha) {
      Napier.v("Need to wait for a fresh pipeline")
      return false
    }
    Napier.v("Pipeline status is ${pipeline.status}")
    return when (pipeline.status) {
      Pipeline.Status.SUCCESS -> true
      Pipeline.Status.FAILED -> throw CannotMergeException("Pipeline failed")
      Pipeline.Status.CANCELED -> throw CannotMergeException("Pipeline was canceled")
      Pipeline.Status.SKIPPED -> if (canMergeWithSkippedPipeline()) {
        true
      } else {
        throw CannotMergeException("Pipeline was skipped and the project requires a successful pipeline")
      }
      else -> false
    }
  }

  suspend fun canMergeWithSkippedPipeline(): Boolean {
    val project = repository.getProject()
    return !project.onlyAllowMergeIfPipelineSucceeds || project.allowMergeOnSkippedPipeline == true
  }

  suspend fun getPipelineOrNull(mergeRequest: MergeRequest): Pipeline? {
    val mergeRequestPipeline = mergeRequest.pipeline
    return mergeRequestPipeline?.id?.let {
      repository.getPipeline(mergeRequestPipeline.id)
    }
  }
}
