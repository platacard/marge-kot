package marge_kot.data.dto.merge_request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiffRefs(
  @SerialName("base_sha")
  val baseSha: String?,
  @SerialName("head_sha")
  val headSha: String?,
)