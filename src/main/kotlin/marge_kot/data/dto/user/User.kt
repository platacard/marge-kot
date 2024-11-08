package marge_kot.data.dto.user

import io.ktor.resources.Resource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Resource("user")
class UserRequest

@Serializable
data class User(
  val id: Int,
  val name: String,
  @SerialName("username")
  val userName: String,
)