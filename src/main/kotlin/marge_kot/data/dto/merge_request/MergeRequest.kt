package marge_kot.data.dto.merge_request

import io.ktor.resources.Resource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import marge_kot.data.dto.common.State
import marge_kot.data.dto.pipeline.Pipeline
import marge_kot.data.dto.user.User

@Resource("{id}")
data class MergeRequestRequest(
  val parent: MergeRequests,
  @SerialName("include_rebase_in_progress")
  val includeRebaseInProgress: Boolean? = null,
  val id: Long,
  @SerialName("assignee_ids")
  val assigneeIds: String? = null,
) {

  @Resource("merge")
  data class Merge(
    val parent: MergeRequestRequest,
  )

  @Resource("notes")
  data class Notes(
    val parent: MergeRequestRequest,
    val body: String,
  )
}

@Serializable
data class MergeRequest(
  @SerialName("iid")
  val id: Long,
  val title: String,
  val state: State,
  @SerialName("target_branch")
  val targetBranch: String,
  @SerialName("source_branch")
  val sourceBranch: String,
  val assignees: List<User>,
  val reviewers: List<User>,
  val draft: Boolean,
  @SerialName("merge_status")
  val mergeStatus: MergeStatus = MergeStatus.UNKNOWN,
  @SerialName("detailed_merge_status")
  val detailedMergeStatus: DetailedMergeStatus = DetailedMergeStatus.UNKNOWN,
  @SerialName("blocking_discussions_resolved")
  val blockingDiscussionsResolved: Boolean,
  @SerialName("rebase_in_progress")
  val rebaseInProgress: Boolean? = null,
  @SerialName("diff_refs")
  val diffRefs: DiffRefs? = null,
  @SerialName("head_pipeline")
  val pipeline: Pipeline? = null,
  @SerialName("has_conflicts")
  val hasConflicts: Boolean? = null,
)