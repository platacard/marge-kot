package marge_kot.data.dto

import io.ktor.resources.Resource

@Resource("/projects/{projectId}")
data class ProjectRequest(
  val projectId: String,
)
