package marge_kot.dto.merge_request

import io.ktor.resources.Resource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Resource("rebase")
data class RebaseRequest(
  val parent: MergeRequestRequest,
)

@Serializable
data class RebaseResult(
  @SerialName("rebase_in_progress")
  val rebaseInProgress: Boolean,
  @SerialName("merge_error")
  val mergeError: String?,
)
