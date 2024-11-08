package marge_kot.helpers

import marge_kot.dto.merge_request.MergeRequest
import marge_kot.repository.Repository

class MergeHelper(
  val repository: Repository,
) {

  suspend fun merge(mergeRequest: MergeRequest) {
    val mergeableChecker = MergeRequestMergeableChecker(repository, mergeRequest.id)
    val rebaseHelper = RebaseHelper(repository, mergeRequest.id)
    while (true) {
      mergeableChecker.check()
      rebaseHelper.rebaseIfNeeded()
      mergeableChecker.check()
    }
  }
}