package marge_kot.helpers

import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import marge_kot.data.Repository
import marge_kot.data.dto.CannotMergeException
import marge_kot.data.dto.NeedRebaseException
import marge_kot.data.dto.pipeline.Pipeline

class PipelineWaiter(
  private val repository: Repository,
  private val mergeRequestId: Long,
) {

  suspend fun waitForPipeline() {
    while (true) {
      delay(3000)
      Napier.v("Update merge request")
      val mergeRequest = repository.getMergeRequest(mergeRequestId)
      mergeRequest.checkForNoConflicts()
      mergeRequest.checkIfUpdated(
        repository = repository,
        onOutdated = { throw NeedRebaseException() }
      )
      Napier.v("Get pipeline for current merge request")
      val mergeRequestPipeline = mergeRequest.pipeline
      val pipeline = mergeRequestPipeline?.id?.let {
        repository.getPipeline(mergeRequestPipeline.id)
      } ?: throw NeedRebaseException()
      Napier.v("Check if pipeline is actual")
      if (mergeRequest.diffRefs?.headSha != pipeline.sha) {
        Napier.v("Need to wait for a fresh pipeline")
        continue
      }
      Napier.v("Pipeline status is ${pipeline.status}")
      when (pipeline.status) {
        Pipeline.Status.SUCCESS -> return
        Pipeline.Status.FAILED -> throw CannotMergeException("Pipeline failed")
        Pipeline.Status.CANCELED -> throw NeedRebaseException()
        Pipeline.Status.SKIPPED -> throw NeedRebaseException()
        else -> continue
      }
    }
  }
}
