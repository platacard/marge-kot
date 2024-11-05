package marge_kot.dto.merge_request

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Resource("approvals")
class MergeRequestApprovalsRequest(val parent: MergeRequestRequest)

@Serializable
data class MergeRequestApprovals(
  val approved: Boolean,
)