package marge_kot.dto.merge_request

import io.ktor.resources.Resource
import marge_kot.dto.common.Scope
import marge_kot.dto.common.State

@Resource("/projects/{projectId}/merge_requests")
class MergeRequestsRequest(
  val projectId: String,
  val scope: Scope? = Scope.ALL,
  val state: State?,
)