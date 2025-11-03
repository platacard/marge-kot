package marge_kot.helpers

import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import marge_kot.data.dto.common.Scope
import marge_kot.data.dto.merge_request.DiffRefs
import marge_kot.data.dto.pipeline.Pipeline
import marge_kot.test_utils.BaseTest
import marge_kot.test_utils.TestUtils.createTestMergeRequest
import marge_kot.test_utils.TestUtils.createTestPipeline
import marge_kot.test_utils.TestUtils.createTestUser
import org.junit.jupiter.api.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LabelHandlerTest : BaseTest(), KoinComponent {

  private val labelHandler: LabelHandler by inject()
  private val testLabel = "test-auto-merge"

  @Test
  fun `processLabeledMergeRequests should do nothing when label is null`() = runTest {
    // Create a LabelHandler with null label
    val mergeableChecker: MergeRequestMergeableChecker by inject()
    val pipelineChecker: PipelineChecker by inject()
    val handlerWithNullLabel = LabelHandler(
      repository = mockRepository,
      mergeableChecker = mergeableChecker,
      pipelineChecker = pipelineChecker,
      label = null,
    )
    
    handlerWithNullLabel.processLabeledMergeRequests("main")

    // Should not make any repository calls when label is null
    coVerify(exactly = 0) { mockRepository.getOpenedMergeRequests(any(), any(), any()) }
    coVerify(exactly = 0) { mockRepository.getUserInfo() }
    coVerify(exactly = 0) { mockRepository.assignMergeRequestTo(any(), any()) }
  }

  @Test
  fun `processLabeledMergeRequests should do nothing when no merge requests found`() = runTest {
    val targetBranch = "main"
    
    coEvery { mockRepository.getOpenedMergeRequests(targetBranch, Scope.ALL, testLabel) } returns emptyList()

    labelHandler.processLabeledMergeRequests(targetBranch)

    coVerify(exactly = 1) { mockRepository.getOpenedMergeRequests(targetBranch, Scope.ALL, testLabel) }
    coVerify(exactly = 0) { mockRepository.getUserInfo() }
    coVerify(exactly = 0) { mockRepository.assignMergeRequestTo(any(), any()) }
  }

  @Test
  fun `processLabeledMergeRequests should assign merge requests when they are mergeable`() = runTest {
    val targetBranch = "main"
    val targetSha = "base123"
    val headSha = "head456"
    val user = createTestUser(id = 100L, username = "test-bot")
    
    val mergeRequest1 = createTestMergeRequest(
      id = 1L,
      targetBranch = targetBranch,
      diffRefs = DiffRefs(baseSha = targetSha, headSha = headSha),
      pipeline = createTestPipeline(id = 1L, sha = headSha, status = Pipeline.Status.SUCCESS),
      labels = listOf(testLabel),
      assignees = emptyList(),
    )
    val mergeRequest2 = createTestMergeRequest(
      id = 2L,
      targetBranch = targetBranch,
      diffRefs = DiffRefs(baseSha = targetSha, headSha = headSha),
      pipeline = createTestPipeline(id = 2L, sha = headSha, status = Pipeline.Status.SUCCESS),
      labels = listOf(testLabel),
      assignees = emptyList(),
    )
    
    coEvery { mockRepository.getOpenedMergeRequests(targetBranch, Scope.ALL, testLabel) } returns listOf(
      mergeRequest1,
      mergeRequest2
    )
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true
    coEvery { mockRepository.checkIfMergeRequestApproved(2L) } returns true
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest1
    coEvery { mockRepository.getMergeRequest(2L) } returns mergeRequest2
    coEvery { mockRepository.getPipeline(1L) } returns createTestPipeline(
      id = 1L,
      sha = headSha,
      status = Pipeline.Status.SUCCESS
    )
    coEvery { mockRepository.getPipeline(2L) } returns createTestPipeline(
      id = 2L,
      sha = headSha,
      status = Pipeline.Status.SUCCESS
    )

    labelHandler.processLabeledMergeRequests(targetBranch)

    coVerify(exactly = 1) { mockRepository.getOpenedMergeRequests(targetBranch, Scope.ALL, testLabel) }
    coVerify(exactly = 1) { mockRepository.getUserInfo() }
    coVerify(exactly = 1) { mockRepository.getMergeRequest(1L) }
    coVerify(exactly = 1) { mockRepository.getMergeRequest(2L) }
    coVerify(exactly = 1) { mockRepository.checkIfMergeRequestApproved(1L) }
    coVerify(exactly = 1) { mockRepository.checkIfMergeRequestApproved(2L) }
    coVerify(exactly = 1) { mockRepository.getPipeline(1L) }
    coVerify(exactly = 1) { mockRepository.getPipeline(2L) }
    coVerify(exactly = 1) { 
      mockRepository.assignMergeRequestTo(1L, listOf(user))
    }
    coVerify(exactly = 1) { 
      mockRepository.assignMergeRequestTo(2L, listOf(user))
    }
  }

  @Test
  fun `processLabeledMergeRequests should skip merge requests that are not mergeable`() = runTest {
    val targetBranch = "main"
    val targetSha = "base123"
    val headSha = "head456"
    val user = createTestUser(id = 100L, username = "test-bot")
    
    val mergeRequestMergeable = createTestMergeRequest(
      id = 1L,
      targetBranch = targetBranch,
      diffRefs = DiffRefs(baseSha = targetSha, headSha = headSha),
      pipeline = createTestPipeline(id = 1L, sha = headSha, status = Pipeline.Status.SUCCESS),
      labels = listOf(testLabel),
      assignees = emptyList(),
    )
    val mergeRequestNotMergeable = createTestMergeRequest(
      id = 2L,
      targetBranch = targetBranch,
      hasConflicts = true,
      labels = listOf(testLabel),
      assignees = emptyList(),
    )
    
    coEvery { mockRepository.getOpenedMergeRequests(targetBranch, Scope.ALL, testLabel) } returns listOf(
      mergeRequestMergeable,
      mergeRequestNotMergeable
    )
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true
    coEvery { mockRepository.checkIfMergeRequestApproved(2L) } returns true
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequestMergeable
    coEvery { mockRepository.getMergeRequest(2L) } returns mergeRequestNotMergeable
    coEvery { mockRepository.getPipeline(1L) } returns createTestPipeline(
      id = 1L,
      sha = headSha,
      status = Pipeline.Status.SUCCESS
    )

    labelHandler.processLabeledMergeRequests(targetBranch)

    // Should check both merge requests
    coVerify(exactly = 1) { mockRepository.getMergeRequest(1L) }
    coVerify(exactly = 1) { mockRepository.getMergeRequest(2L) }
    
    // Should only assign the mergeable one
    coVerify(exactly = 1) { 
      mockRepository.assignMergeRequestTo(1L, listOf(user))
    }
    coVerify(exactly = 0) { 
      mockRepository.assignMergeRequestTo(2L, any())
    }
  }

  @Test
  fun `processLabeledMergeRequests should skip merge requests that are not approved`() = runTest {
    val targetBranch = "main"
    val targetSha = "base123"
    val headSha = "head456"
    val user = createTestUser(id = 100L, username = "test-bot")
    
    val mergeRequestApproved = createTestMergeRequest(
      id = 1L,
      targetBranch = targetBranch,
      diffRefs = DiffRefs(baseSha = targetSha, headSha = headSha),
      pipeline = createTestPipeline(id = 1L, sha = headSha, status = Pipeline.Status.SUCCESS),
      labels = listOf(testLabel),
      assignees = emptyList(),
    )
    val mergeRequestNotApproved = createTestMergeRequest(
      id = 2L,
      targetBranch = targetBranch,
      diffRefs = DiffRefs(baseSha = targetSha, headSha = headSha),
      pipeline = createTestPipeline(id = 2L, sha = headSha, status = Pipeline.Status.SUCCESS),
      labels = listOf(testLabel),
      assignees = emptyList(),
    )
    
    coEvery { mockRepository.getOpenedMergeRequests(targetBranch, Scope.ALL, testLabel) } returns listOf(
      mergeRequestApproved,
      mergeRequestNotApproved
    )
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true
    coEvery { mockRepository.checkIfMergeRequestApproved(2L) } returns false
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequestApproved
    coEvery { mockRepository.getMergeRequest(2L) } returns mergeRequestNotApproved
    coEvery { mockRepository.getPipeline(1L) } returns createTestPipeline(
      id = 1L,
      sha = headSha,
      status = Pipeline.Status.SUCCESS
    )

    labelHandler.processLabeledMergeRequests(targetBranch)

    // Should only assign the approved one
    coVerify(exactly = 1) { 
      mockRepository.assignMergeRequestTo(1L, listOf(user))
    }
    coVerify(exactly = 0) { 
      mockRepository.assignMergeRequestTo(2L, any())
    }
  }

  @Test
  fun `processLabeledMergeRequests should skip merge requests with failed pipelines`() = runTest {
    val targetBranch = "main"
    val targetSha = "base123"
    val headSha = "head456"
    val user = createTestUser(id = 100L, username = "test-bot")
    
    val mergeRequestWithSuccessPipeline = createTestMergeRequest(
      id = 1L,
      targetBranch = targetBranch,
      diffRefs = DiffRefs(baseSha = targetSha, headSha = headSha),
      pipeline = createTestPipeline(id = 1L, sha = headSha, status = Pipeline.Status.SUCCESS),
      labels = listOf(testLabel),
      assignees = emptyList(),
    )
    val mergeRequestWithFailedPipeline = createTestMergeRequest(
      id = 2L,
      targetBranch = targetBranch,
      diffRefs = DiffRefs(baseSha = targetSha, headSha = headSha),
      pipeline = createTestPipeline(id = 2L, sha = headSha, status = Pipeline.Status.FAILED),
      labels = listOf(testLabel),
      assignees = emptyList(),
    )
    
    coEvery { mockRepository.getOpenedMergeRequests(targetBranch, Scope.ALL, testLabel) } returns listOf(
      mergeRequestWithSuccessPipeline,
      mergeRequestWithFailedPipeline
    )
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true
    coEvery { mockRepository.checkIfMergeRequestApproved(2L) } returns true
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequestWithSuccessPipeline
    coEvery { mockRepository.getMergeRequest(2L) } returns mergeRequestWithFailedPipeline
    coEvery { mockRepository.getPipeline(1L) } returns createTestPipeline(
      id = 1L,
      sha = headSha,
      status = Pipeline.Status.SUCCESS
    )
    coEvery { mockRepository.getPipeline(2L) } returns createTestPipeline(
      id = 2L,
      sha = headSha,
      status = Pipeline.Status.FAILED
    )

    labelHandler.processLabeledMergeRequests(targetBranch)

    // Should only assign the one with successful pipeline
    coVerify(exactly = 1) { 
      mockRepository.assignMergeRequestTo(1L, listOf(user))
    }
    coVerify(exactly = 0) { 
      mockRepository.assignMergeRequestTo(2L, any())
    }
  }

  @Test
  fun `processLabeledMergeRequests should preserve existing assignees when assigning`() = runTest {
    val targetBranch = "main"
    val targetSha = "base123"
    val headSha = "head456"
    val existingUser = createTestUser(id = 50L, username = "existing-user")
    val botUser = createTestUser(id = 100L, username = "test-bot")
    
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = targetBranch,
      diffRefs = DiffRefs(baseSha = targetSha, headSha = headSha),
      pipeline = createTestPipeline(id = 1L, sha = headSha, status = Pipeline.Status.SUCCESS),
      labels = listOf(testLabel),
      assignees = listOf(existingUser),
    )
    
    coEvery { mockRepository.getOpenedMergeRequests(targetBranch, Scope.ALL, testLabel) } returns listOf(mergeRequest)
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true
    coEvery { mockRepository.getUserInfo() } returns botUser
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.getPipeline(1L) } returns createTestPipeline(
      id = 1L,
      sha = headSha,
      status = Pipeline.Status.SUCCESS
    )

    labelHandler.processLabeledMergeRequests(targetBranch)

    // Should assign bot to merge request, preserving existing assignees
    coVerify(exactly = 1) { 
      mockRepository.assignMergeRequestTo(1L, listOf(existingUser, botUser))
    }
  }

  @Test
  fun `processLabeledMergeRequests should handle merge requests with no pipeline gracefully`() = runTest {
    val targetBranch = "main"
    val targetSha = "base123"
    val headSha = "head456"
    
    val mergeRequestNoPipeline = createTestMergeRequest(
      id = 1L,
      targetBranch = targetBranch,
      diffRefs = DiffRefs(baseSha = targetSha, headSha = headSha),
      pipeline = null,
      labels = listOf(testLabel),
      assignees = emptyList(),
    )
    
    coEvery { mockRepository.getOpenedMergeRequests(targetBranch, Scope.ALL, testLabel) } returns listOf(mergeRequestNoPipeline)
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequestNoPipeline

    labelHandler.processLabeledMergeRequests(targetBranch)

    // Should not assign merge requests without pipelines
    coVerify(exactly = 0) { mockRepository.assignMergeRequestTo(any(), any()) }
    coVerify(exactly = 0) { mockRepository.getUserInfo() }
  }
}
