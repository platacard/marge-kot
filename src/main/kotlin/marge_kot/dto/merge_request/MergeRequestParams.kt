package marge_kot.dto.merge_request

import kotlinx.serialization.SerialName

enum class MergeStatus {
  @SerialName("can_be_merged")
  CAN_BE_MERGED,

  @SerialName("merged")
  MERGED,

  @SerialName("unknown")
  UNKNOWN,
}

enum class DetailedMergeStatus {
  @SerialName("not_open")
  NOT_OPEN,

  @SerialName("not_approved")
  NOT_APPROVED,

  @SerialName("broken_status")
  BROKEN_STATUS,

  @SerialName("unknown")
  UNKNOWN,
}