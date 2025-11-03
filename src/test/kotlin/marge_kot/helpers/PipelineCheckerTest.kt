package marge_kot.helpers

import com.google.common.truth.Truth.assertThat
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
import marge_kot.test_utils.TestUtils.createTestBranch
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PipelineCheckerTest : BaseTest(), KoinComponent {

  private val pipelineChecker: PipelineChecker by inject()

  @BeforeEach
  override fun setUp() {
    super.setUp()
  }

  @Test
  fun `checkIfPipelineFinished should return true when pipeline is successful`() = runTest {
    val pipeline = createTestPipeline(id = 1L, sha = "head456", status = Pipeline.Status.SUCCESS)
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      diffRefs = DiffRefs(baseSha = "base123", headSha = "head456"),
      pipeline = pipeline,
    )
    
    coEvery { mockRepository.getPipeline(1L) } returns pipeline
    val result = pipelineChecker.checkIfPipelineFinished(mergeRequest)

    assertThat(result).isTrue()
    coVerify { mockRepository.getPipeline(1L) }
  }

  @Test
  fun `checkIfPipelineFinished should throw CannotMergeException when pipeline failed`() = runTest {
    val pipeline = createTestPipeline(id = 1L, sha = "head456", status = Pipeline.Status.FAILED)
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      diffRefs = DiffRefs(baseSha = "base123", headSha = "head456"),
      pipeline = pipeline,
    )
    
    coEvery { mockRepository.getPipeline(1L) } returns pipeline
    val exception = assertThrows<CannotMergeException> {
      pipelineChecker.checkIfPipelineFinished(mergeRequest)
    }

    assertThat(exception.message).contains("failed")
    coVerify { mockRepository.getPipeline(1L) }
  }

  @Test
  fun `checkIfPipelineFinished should throw CannotMergeException when pipeline is canceled`() = runTest {
    val pipeline = createTestPipeline(id = 1L, sha = "head456", status = Pipeline.Status.CANCELED)
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      diffRefs = DiffRefs(baseSha = "base123", headSha = "head456"),
      pipeline = pipeline,
    )
    
    coEvery { mockRepository.getPipeline(1L) } returns pipeline
    val exception = assertThrows<CannotMergeException> {
      pipelineChecker.checkIfPipelineFinished(mergeRequest)
    }

    assertThat(exception.message).contains("canceled")
    coVerify { mockRepository.getPipeline(1L) }
  }

  @Test
  fun `checkIfPipelineFinished should throw NeedRebaseException when pipeline is skipped`() = runTest {
    val pipeline = createTestPipeline(id = 1L, sha = "head456", status = Pipeline.Status.SKIPPED)
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      diffRefs = DiffRefs(baseSha = "base123", headSha = "head456"),
      pipeline = pipeline,
    )
    
    coEvery { mockRepository.getPipeline(1L) } returns pipeline
    assertThrows<NeedRebaseException> {
      pipelineChecker.checkIfPipelineFinished(mergeRequest)
    }

    coVerify { mockRepository.getPipeline(1L) }
  }

  @Test
  fun `checkIfPipelineFinished should throw NeedRebaseException when pipeline is null`() = runTest {
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      diffRefs = DiffRefs(baseSha = "base123", headSha = "head456"),
      pipeline = null,
    )
    
    assertThrows<NeedRebaseException> {
      pipelineChecker.checkIfPipelineFinished(mergeRequest)
    }

    coVerify(exactly = 0) { mockRepository.getPipeline(any()) }
  }

  @Test
  fun `checkIfPipelineFinished should return false when pipeline sha does not match merge request head sha`() = runTest {
    val pipeline = createTestPipeline(id = 1L, sha = "differentSha", status = Pipeline.Status.SUCCESS)
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      diffRefs = DiffRefs(baseSha = "base123", headSha = "head456"),
      pipeline = pipeline,
    )
    
    coEvery { mockRepository.getPipeline(1L) } returns pipeline
    val result = pipelineChecker.checkIfPipelineFinished(mergeRequest)

    assertThat(result).isFalse()
    coVerify { mockRepository.getPipeline(1L) }
  }

  @Test
  fun `checkIfPipelineFinished should return false when pipeline is running`() = runTest {
    val pipeline = createTestPipeline(id = 1L, sha = "head456", status = Pipeline.Status.RUNNING)
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      diffRefs = DiffRefs(baseSha = "base123", headSha = "head456"),
      pipeline = pipeline,
    )
    
    val result = pipelineChecker.checkIfPipelineFinished(mergeRequest)

    assertThat(result).isFalse()
    coVerify { mockRepository.getPipeline(1L) }
  }

  @Test
  fun `waitForPipeline should return when pipeline finishes successfully`() = runTest {
    val targetBranch = "main"
    val targetSha = "base123"
    val headSha = "head456"
    val pipelineRunning = createTestPipeline(id = 1L, sha = headSha, status = Pipeline.Status.RUNNING)
    val pipelineSuccess = createTestPipeline(id = 1L, sha = headSha, status = Pipeline.Status.SUCCESS)
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = targetBranch,
      diffRefs = DiffRefs(baseSha = targetSha, headSha = headSha),
      pipeline = pipelineRunning,
    )
    val branch = createTestBranch(name = targetBranch, commitSha = targetSha)
    val user = marge_kot.test_utils.TestUtils.createTestUser(id = 1L)
    
    coEvery { mockRepository.getMergeRequest(1L) } returnsMany listOf(mergeRequest, mergeRequest)
    coEvery { mockRepository.getPipeline(1L) } returnsMany listOf(pipelineRunning, pipelineSuccess)
    coEvery { mockRepository.getBranchInfo(targetBranch) } returns branch
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true

    pipelineChecker.waitForPipeline(1L)

    coVerify(atLeast = 1) { mockRepository.getMergeRequest(1L) }
    coVerify(atLeast = 1) { mockRepository.getPipeline(1L) }
  }

  @Test
  fun `waitForPipeline should throw CannotMergeException when pipeline fails`() = runTest {
    val targetBranch = "main"
    val targetSha = "base123"
    val headSha = "head456"
    val pipelineFailed = createTestPipeline(id = 1L, sha = headSha, status = Pipeline.Status.FAILED)
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = targetBranch,
      diffRefs = DiffRefs(baseSha = targetSha, headSha = headSha),
      pipeline = pipelineFailed,
    )
    val branch = createTestBranch(name = targetBranch, commitSha = targetSha)
    val user = marge_kot.test_utils.TestUtils.createTestUser(id = 1L)
    
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.getPipeline(1L) } returns pipelineFailed
    coEvery { mockRepository.getBranchInfo(targetBranch) } returns branch
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true

    assertThrows<CannotMergeException> {
      pipelineChecker.waitForPipeline(1L)
    }

    coVerify { mockRepository.getMergeRequest(1L) }
    coVerify { mockRepository.getPipeline(1L) }
  }

  @Test
  fun `waitForPipeline should throw NeedRebaseException when merge request is outdated`() = runTest {
    val targetBranch = "main"
    val oldTargetSha = "oldBase123"
    val newTargetSha = "newBase123"
    val headSha = "head456"
    val pipelineRunning = createTestPipeline(id = 1L, sha = headSha, status = Pipeline.Status.RUNNING)
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = targetBranch,
      diffRefs = DiffRefs(baseSha = oldTargetSha, headSha = headSha),
      pipeline = pipelineRunning,
    )
    val branch = createTestBranch(name = targetBranch, commitSha = newTargetSha)
    val user = marge_kot.test_utils.TestUtils.createTestUser(id = 1L)
    
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.getPipeline(1L) } returns pipelineRunning
    coEvery { mockRepository.getBranchInfo(targetBranch) } returns branch
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true

    assertThrows<NeedRebaseException> {
      pipelineChecker.waitForPipeline(1L)
    }

    coVerify { mockRepository.getMergeRequest(1L) }
    coVerify { mockRepository.getBranchInfo(targetBranch) }
  }
}

