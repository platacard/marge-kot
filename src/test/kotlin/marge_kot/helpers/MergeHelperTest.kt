package marge_kot.helpers

import com.google.common.truth.Truth.assertThat
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import marge_kot.data.Repository
import marge_kot.data.dto.CannotMergeException
import marge_kot.data.dto.NeedRebaseException
import marge_kot.data.dto.merge_request.DiffRefs
import marge_kot.data.dto.pipeline.Pipeline
import marge_kot.test_utils.BaseTest
import marge_kot.test_utils.TestUtils.createTestMergeRequest
import marge_kot.test_utils.TestUtils.createTestPipeline
import marge_kot.test_utils.TestUtils.createTestUser
import marge_kot.test_utils.TestUtils.createTestBranch
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import io.mockk.mockk
import marge_kot.data.dto.merge_request.RebaseResult

class MergeHelperTest : BaseTest(), KoinComponent {

  private val mergeHelper: MergeHelper by inject()

  @BeforeEach
  override fun setUp() {
    super.setUp()
  }

  @Test
  fun `merge should successfully merge when all checks pass`() = runTest {
    val targetBranch = "main"
    val targetSha = "base123"
    val headSha = "head456"
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = targetBranch,
      diffRefs = DiffRefs(baseSha = targetSha, headSha = headSha),
      pipeline = createTestPipeline(id = 1L, sha = headSha, status = Pipeline.Status.SUCCESS),
    )
    val branch = createTestBranch(name = targetBranch, commitSha = targetSha)
    val user = createTestUser(id = 1L)
    
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.getBranchInfo(targetBranch) } returns branch
    coEvery { mockRepository.getPipeline(1L) } returns createTestPipeline(
      id = 1L,
      sha = headSha,
      status = Pipeline.Status.SUCCESS
    )
    coEvery { mockRepository.rebaseMergeRequest(any()) } returns RebaseResult(
      rebaseInProgress = false,
      mergeError = null
    )

    mergeHelper.merge(1L)

    coVerify { mockRepository.merge(1L) }
    coVerify(exactly = 0) { mockRepository.addCommentToMergeRequest(any(), any()) }
    coVerify(exactly = 0) { mockRepository.assignMergeRequestTo(any(), any()) }
  }

  @Test
  fun `merge should retry on NeedRebaseException`() = runTest {
    val targetBranch = "main"
    val targetSha = "base123"
    val headSha = "head456"
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = targetBranch,
      diffRefs = DiffRefs(baseSha = targetSha, headSha = headSha),
      pipeline = createTestPipeline(id = 1L, sha = headSha, status = Pipeline.Status.SUCCESS),
    )
    val branch = createTestBranch(name = targetBranch, commitSha = targetSha)
    val user = createTestUser(id = 1L)
    
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.getBranchInfo(targetBranch) } returns branch
    coEvery { mockRepository.getPipeline(1L) } returnsMany listOf(
      createTestPipeline(id = 1L, sha = headSha, status = Pipeline.Status.SKIPPED),
      createTestPipeline(id = 1L, sha = headSha, status = Pipeline.Status.SUCCESS)
    )
    coEvery { mockRepository.rebaseMergeRequest(any()) } returns RebaseResult(
      rebaseInProgress = false,
      mergeError = null
    )
    coEvery { mockRepository.addCommentToMergeRequest(any(), any()) } returns Unit

    mergeHelper.merge(1L)

    coVerify { mockRepository.merge(1L) }
    coVerify(atLeast = 1) { mockRepository.addCommentToMergeRequest(1L, "Hmm, I guess I should try again") }
  }

  @Test
  fun `merge should unassign bot and comment when CannotMergeException is thrown`() = runTest {
    val targetBranch = "main"
    val targetSha = "base123"
    val headSha = "head456"
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = targetBranch,
      diffRefs = DiffRefs(baseSha = targetSha, headSha = headSha),
      hasConflicts = true,
    )
    val user = createTestUser(id = 1L)
    
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true
    coEvery { mockRepository.getUserInfo() } returns user

    mergeHelper.merge(1L)

    coVerify(exactly = 0) { mockRepository.merge(any()) }
    coVerify { mockRepository.addCommentToMergeRequest(1L, match { it.contains("can't merge") }) }
    coVerify { mockRepository.getUserInfo() }
    coVerify { 
      mockRepository.assignMergeRequestTo(
        1L,
        match { assignees -> assignees.none { it.id == user.id } }
      )
    }
  }

  @Test
  fun `merge should unassign bot and comment when ServerResponseException is thrown`() = runTest {
    val targetBranch = "main"
    val targetSha = "base123"
    val headSha = "head456"
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = targetBranch,
      diffRefs = DiffRefs(baseSha = targetSha, headSha = headSha),
    )
    val user = createTestUser(id = 1L)
    val exception = io.mockk.mockk<ServerResponseException>(relaxed = true)
    
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.getBranchInfo(any()) } throws exception
    coEvery { exception.message } returns "Server error"

    mergeHelper.merge(1L)

    coVerify(exactly = 0) { mockRepository.merge(any()) }
    coVerify { mockRepository.addCommentToMergeRequest(1L, match { it.contains("Something happened with Gitlab") }) }
    coVerify { 
      mockRepository.assignMergeRequestTo(
        1L,
        match { assignees -> assignees.none { it.id == user.id } }
      )
    }
  }

  @Test
  fun `merge should unassign bot and comment when HttpRequestTimeoutException is thrown`() = runTest {
    val targetBranch = "main"
    val targetSha = "base123"
    val headSha = "head456"
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = targetBranch,
      diffRefs = DiffRefs(baseSha = targetSha, headSha = headSha),
    )
    val user = createTestUser(id = 1L)
    val exception = io.mockk.mockk<HttpRequestTimeoutException>(relaxed = true)
    
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.getBranchInfo(any()) } throws exception
    coEvery { exception.message } returns "Timeout"

    mergeHelper.merge(1L)

    coVerify(exactly = 0) { mockRepository.merge(any()) }
    coVerify { mockRepository.addCommentToMergeRequest(1L, match { it.contains("Gitlab is not responding") }) }
    coVerify { 
      mockRepository.assignMergeRequestTo(
        1L,
        match { assignees -> assignees.none { it.id == user.id } }
      )
    }
  }

  @Test
  fun `merge should unassign bot and comment on unhandled exception`() = runTest {
    val targetBranch = "main"
    val targetSha = "base123"
    val headSha = "head456"
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = targetBranch,
      diffRefs = DiffRefs(baseSha = targetSha, headSha = headSha),
    )
    val user = createTestUser(id = 1L)
    
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } throws RuntimeException("Unexpected error")
    coEvery { mockRepository.getUserInfo() } returns user

    mergeHelper.merge(1L)

    coVerify(exactly = 0) { mockRepository.merge(any()) }
    coVerify { mockRepository.addCommentToMergeRequest(1L, match { it.contains("Something bad happened") }) }
    coVerify { 
      mockRepository.assignMergeRequestTo(
        1L,
        match { assignees -> assignees.none { it.id == user.id } }
      )
    }
  }
}

