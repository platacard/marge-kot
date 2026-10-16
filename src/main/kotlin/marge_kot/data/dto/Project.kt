package marge_kot.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Project(
  @SerialName("only_allow_merge_if_pipeline_succeeds")
  val onlyAllowMergeIfPipelineSucceeds: Boolean = false,
  @SerialName("allow_merge_on_skipped_pipeline")
  val allowMergeOnSkippedPipeline: Boolean? = null,
)
