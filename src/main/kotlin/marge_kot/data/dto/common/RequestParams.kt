package marge_kot.data.dto.common

import kotlinx.serialization.SerialName

enum class Scope {
  @SerialName("created_by_me")
  CREATED_BY_ME,

  @SerialName("assigned_to_me")
  ASSIGNED_TO_ME,

  @SerialName("all")
  ALL,
  ;
}

enum class State {
  @SerialName("opened")
  OPENED,

  @SerialName("closed")
  CLOSED,

  @SerialName("locked")
  LOCKED,

  @SerialName("merged")
  MERGED,
}

enum class OrderBy {
  @SerialName("updated_at")
  UPDATED_AT,

  @SerialName("created_at")
  CREATED_AT,
}

enum class Sort {
  @SerialName("desc")
  DESCENDING,

  @SerialName("asc")
  ASCENDING
}
