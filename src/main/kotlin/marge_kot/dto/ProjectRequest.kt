package marge_kot.dto

import io.ktor.resources.Resource

@Resource("/projects/{projectId}")
data class ProjectRequest(
  val projectId: String,
)
