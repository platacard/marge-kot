package marge_kot.data.dto.merge_request

import io.ktor.resources.Resource
import kotlinx.serialization.SerialName
import marge_kot.data.dto.ProjectRequest
import marge_kot.data.dto.common.Scope
import marge_kot.data.dto.common.State

@Resource("merge_requests")
class MergeRequests(
  val parent: ProjectRequest,
  val state: State?,
  val scope: Scope? = Scope.ALL,
  @SerialName("target_branch")
  val targetBranch: String? = null
)