package marge_kot.dto.merge_request

import io.ktor.resources.Resource
import marge_kot.dto.ProjectRequest
import marge_kot.dto.common.Scope
import marge_kot.dto.common.State

@Resource("merge_requests")
class MergeRequestsRequest(
  val parent: ProjectRequest,
  val scope: Scope? = Scope.ALL,
  val state: State?,
)