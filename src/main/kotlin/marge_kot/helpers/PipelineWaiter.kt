package marge_kot.helpers

import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import marge_kot.data.Repository
import marge_kot.data.dto.pipeline.Pipeline
import marge_kot.utils.CannotMergeException

class PipelineWaiter(
  private val repository: Repository,
  private val mergeRequestId: Long,
) {

  suspend fun waitForPipeline() {
    while (true) {
      delay(3000)
      Napier.v("Update merge request")
      val mergeRequest = repository.getMergeRequest(mergeRequestId)
      Napier.v("Get pipeline for current merge request")
      val pipeline = mergeRequest.pipeline?.id?.let {
        repository.getPipeline(mergeRequest.pipeline.id)
      } ?: throw NoPipelineFoundException()
      Napier.v("Pipeline status is ${pipeline.status}")
      when (pipeline.status) {
        Pipeline.Status.SUCCESS -> return
        Pipeline.Status.FAILED -> throw CannotMergeException("Pipeline failed")
        Pipeline.Status.CANCELED -> throw PipelineCancelledException()
        Pipeline.Status.SKIPPED -> throw PipelineSkippedException()
        else -> continue
      }
    }
  }
}

class PipelineCancelledException : Throwable()
class PipelineSkippedException : Throwable()
class NoPipelineFoundException : Throwable()
