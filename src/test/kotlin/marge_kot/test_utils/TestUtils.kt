package marge_kot.test_utils

import io.mockk.mockk
import marge_kot.data.Repository
import marge_kot.data.dto.git.Branch
import marge_kot.data.dto.git.Commit
import marge_kot.data.dto.merge_request.DiffRefs
import marge_kot.data.dto.merge_request.MergeRequest
import marge_kot.data.dto.merge_request.MergeRequestApprovals
import marge_kot.data.dto.merge_request.MergeStatus
import marge_kot.data.dto.pipeline.Pipeline
import marge_kot.data.dto.user.User
import marge_kot.data.dto.common.State
import marge_kot.helpers.MergeRequestMergeableChecker
import marge_kot.helpers.PipelineChecker

object TestUtils {
  fun createTestUser(id: Long = 1L, name: String = "Test User", username: String = "testuser"): User {
    return User(id = id, name = name, userName = username)
  }

  fun createTestMergeRequest(
    id: Long = 1L,
    title: String = "Test MR",
    targetBranch: String = "main",
    sourceBranch: String = "feature",
    assignees: List<User> = listOf(createTestUser()),
    draft: Boolean = false,
    mergeStatus: MergeStatus = MergeStatus.CAN_BE_MERGED,
    blockingDiscussionsResolved: Boolean = true,
    rebaseInProgress: Boolean? = null,
    diffRefs: DiffRefs? = DiffRefs(baseSha = "base123", headSha = "head456"),
    pipeline: Pipeline? = null,
    hasConflicts: Boolean? = false,
    labels: List<String> = emptyList(),
  ): MergeRequest {
    return MergeRequest(
      id = id,
      title = title,
      state = State.OPENED,
      targetBranch = targetBranch,
      sourceBranch = sourceBranch,
      assignees = assignees,
      reviewers = emptyList(),
      draft = draft,
      mergeStatus = mergeStatus,
      detailedMergeStatus = marge_kot.data.dto.merge_request.DetailedMergeStatus.UNKNOWN,
      blockingDiscussionsResolved = blockingDiscussionsResolved,
      rebaseInProgress = rebaseInProgress,
      diffRefs = diffRefs,
      pipeline = pipeline,
      hasConflicts = hasConflicts,
      labels = labels,
    )
  }

  fun createTestPipeline(
    id: Long = 1L,
    sha: String = "head456",
    status: Pipeline.Status = Pipeline.Status.SUCCESS,
  ): Pipeline {
    return Pipeline(id = id, sha = sha, status = status)
  }

  fun createTestBranch(name: String = "main", commitSha: String = "base123"): Branch {
    return Branch(
      name = name,
      commit = Commit(id = commitSha)
    )
  }

  fun createTestMergeRequestApprovals(approved: Boolean = true, approvalsLeft: Int = 0): MergeRequestApprovals {
    return MergeRequestApprovals(approved = approved, approvalsLeft = approvalsLeft)
  }
}

