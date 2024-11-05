package marge_kot.dto.merge_request

import io.ktor.resources.Resource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import marge_kot.dto.common.Scope
import marge_kot.dto.common.State

@Resource("/merge_requests")
class MergeRequestsRequest(
  val scope: Scope,
  val state: State,
)

@Serializable
class MergeRequest(
  @SerialName("iid")
  val id: Int,
)