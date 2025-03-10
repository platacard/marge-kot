package marge_kot.helpers

import marge_kot.data.Repository
import marge_kot.data.dto.CannotMergeException
import marge_kot.data.dto.merge_request.MergeRequest

suspend inline fun MergeRequest.checkIfUpdated(
  repository: Repository,
  onUpdated: () -> Unit = {},
  onOutdated: () -> Unit = {},
) {
  val targetSha = repository.getBranchInfo(targetBranch).commit.id
  if (diffRefs?.baseSha == null || diffRefs.headSha == null) {
    throw CannotMergeException("Merge request contains no changes")
  }
  if (diffRefs.baseSha == targetSha) onUpdated() else onOutdated()
}