package marge_kot.dto.git

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable
import marge_kot.dto.ProjectRequest

@Resource("repository/branches/{name}")
data class BranchRequest(
  val parent: ProjectRequest,
  val name: String,
)

@Serializable
data class Branch(
  val name: String,
  val commit: Commit,
)