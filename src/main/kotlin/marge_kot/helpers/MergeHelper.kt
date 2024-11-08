package marge_kot.helpers

import marge_kot.data.Repository
import marge_kot.data.dto.merge_request.MergeRequest

class MergeHelper(
  val repository: Repository,
) {

  suspend fun merge(mergeRequest: MergeRequest) {
    val mergeableChecker = MergeRequestMergeableChecker(repository, mergeRequest.id)
    val rebaseHelper = RebaseHelper(repository, mergeRequest.id)
    val pipelineWaiter = PipelineWaiter(repository, mergeRequest.id)
    while (true) {
      mergeableChecker.check()
      rebaseHelper.rebaseIfNeeded()
      pipelineWaiter.waitForPipeline()
      mergeableChecker.check()
    }
  }
}