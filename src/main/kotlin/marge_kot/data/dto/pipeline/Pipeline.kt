package marge_kot.data.dto.pipeline

import io.ktor.resources.Resource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import marge_kot.data.dto.ProjectRequest

@Resource("pipelines/{id}")
data class PipelineRequest(
  val parent: ProjectRequest,
  val id: Long,
)

@Serializable
data class Pipeline(
  val id: Long,
  val sha: String,
  val status: Status = Status.UNKNOWN,
) {

  enum class Status {

    @SerialName("success")
    SUCCESS,

    @SerialName("failed")
    FAILED,

    @SerialName("canceled")
    CANCELED,

    @SerialName("skipped")
    SKIPPED,

    @SerialName("running")
    RUNNING,

    @SerialName("created")
    CREATED,

    @SerialName("waiting_for_resource")
    WAITING_FOR_RESOURCE,

    @SerialName("preparing")
    PREPARING,

    @SerialName("pending")
    PENDING,

    @SerialName("manual")
    MANUAL,

    @SerialName("scheduled")
    SCHEDULED,

    UNKNOWN,
  }
}
