package marge_kot.helpers

import marge_kot.data.dto.Project
import com.google.common.truth.Truth.assertThat
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.mockk.coEvery
import io.mockk.coVerify
import org.junit.jupiter.api.assertThrows
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.TestScope
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
    coEvery { mockRepository.getBranchInfo(targetBranch) } returnsMany listOf(
      branch,
      createTestBranch(name = targetBranch, commitSha = "moved789"),
      branch,
    )
    coEvery { mockRepository.getPipeline(1L) } returns createTestPipeline(
      id = 1L,
      sha = headSha,
      status = Pipeline.Status.SUCCESS
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

  @Test
  fun `merge should still unassign bot when posting the failure comment fails`() = runTest {
    val user = createTestUser(id = 1L)
    val mergeRequest = createTestMergeRequest(id = 1L, draft = true, assignees = listOf(user))

    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.addCommentToMergeRequest(any(), any()) } throws RuntimeException("GitLab is down")

    mergeHelper.merge(1L)

    coVerify { mockRepository.assignMergeRequestTo(1L, emptyList()) }
  }

  @Test
  fun `merge should propagate failure to unassign the bot after commenting`() = runTest {
    val user = createTestUser(id = 1L)
    val mergeRequest = createTestMergeRequest(id = 1L, draft = true, assignees = listOf(user))

    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.assignMergeRequestTo(any(), any()) } throws RuntimeException("GitLab is down")

    assertThrows<RuntimeException> { mergeHelper.merge(1L) }

    coVerify(exactly = 0) { mockRepository.merge(any()) }
    coVerify(exactly = 1) { mockRepository.addCommentToMergeRequest(1L, "I can't merge this merge request: I can't merge drafts") }
  }

  @Test
  fun `merge should propagate cancellation while giving up`() = runTest {
    val user = createTestUser(id = 1L)
    val mergeRequest = createTestMergeRequest(id = 1L, draft = true, assignees = listOf(user))

    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.addCommentToMergeRequest(any(), any()) } throws CancellationException("Shutdown")

    assertThrows<CancellationException> { mergeHelper.merge(1L) }
  }

  @Test
  fun `merge should give up when pipeline of up to date merge request is skipped`() = runTest(timeout = 5.seconds) {
    val user = createTestUser(id = 1L)
    val skippedPipeline = createTestPipeline(id = 1L, sha = "head456", status = Pipeline.Status.SKIPPED)
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      assignees = listOf(user),
      diffRefs = DiffRefs(baseSha = "base123", headSha = "head456"),
      pipeline = skippedPipeline,
    )

    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.getBranchInfo("main") } returns createTestBranch(name = "main", commitSha = "base123")
    coEvery { mockRepository.getPipeline(1L) } returns skippedPipeline
    coEvery { mockRepository.getProject() } returns Project(onlyAllowMergeIfPipelineSucceeds = true, allowMergeOnSkippedPipeline = false)

    mergeHelper.merge(1L)

    coVerify(exactly = 0) { mockRepository.merge(any()) }
    coVerify(exactly = 0) { mockRepository.addCommentToMergeRequest(1L, "Hmm, I guess I should try again") }
    coVerify { mockRepository.addCommentToMergeRequest(1L, "I can't merge this merge request: Pipeline was skipped and the project requires a successful pipeline") }
    coVerify { mockRepository.assignMergeRequestTo(1L, emptyList()) }
  }

  @Test
  fun `merge should give up when target branch keeps moving during pipeline`() = runTest(timeout = 5.seconds) {
    val user = createTestUser(id = 1L)
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      assignees = listOf(user),
      diffRefs = DiffRefs(baseSha = "base123", headSha = "head456"),
      pipeline = createTestPipeline(id = 1L, sha = "head456", status = Pipeline.Status.RUNNING),
    )
    var branchInfoCalls = 0

    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.getBranchInfo("main") } answers {
      val sha = if (branchInfoCalls++ % 2 == 0) "base123" else "moved789"
      createTestBranch(name = "main", commitSha = sha)
    }

    mergeHelper.merge(1L)

    coVerify(exactly = 0) { mockRepository.merge(any()) }
    coVerify(exactly = 5) { mockRepository.addCommentToMergeRequest(1L, "Hmm, I guess I should try again") }
    coVerify { mockRepository.addCommentToMergeRequest(1L, match { it.contains("target branch kept moving") && it.endsWith("Assign me again to retry") }) }
    coVerify { mockRepository.assignMergeRequestTo(1L, emptyList()) }
  }

  @Test
  fun `merge should give up when pipeline waits for manual action until the deadline`() = runTest(timeout = 5.seconds) {
    givenUpToDateMergeRequest(createTestPipeline(id = 1L, sha = "head456", status = Pipeline.Status.MANUAL))

    mergeHelper.merge(1L)

    thenGaveUpOnDeadline()
  }

  @Test
  fun `merge should give up at the overall ceiling when target keeps moving within the attempt deadline`() = runTest(timeout = 5.seconds) {
    val user = createTestUser(id = 1L)
    val pipeline = createTestPipeline(id = 1L, sha = "head456", status = Pipeline.Status.RUNNING)
    var mergeRequestBaseSha = "target0"
    fun targetSha() = "target${testScheduler.currentTime / 60.minutes.inWholeMilliseconds}"

    coEvery { mockRepository.getMergeRequest(1L) } answers {
      createTestMergeRequest(
        id = 1L,
        assignees = listOf(user),
        diffRefs = DiffRefs(baseSha = mergeRequestBaseSha, headSha = "head456"),
        pipeline = pipeline,
      )
    }
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.getBranchInfo("main") } answers { createTestBranch(name = "main", commitSha = targetSha()) }
    coEvery { mockRepository.rebaseMergeRequest(1L) } answers {
      mergeRequestBaseSha = targetSha()
      RebaseResult(rebaseInProgress = false, mergeError = null)
    }
    coEvery { mockRepository.getPipeline(1L) } returns pipeline

    mergeHelper.merge(1L)

    assertThat(testScheduler.currentTime).isAtLeast(270.minutes.inWholeMilliseconds)
    assertThat(testScheduler.currentTime).isLessThan(271.minutes.inWholeMilliseconds)
    coVerify(exactly = 4) { mockRepository.addCommentToMergeRequest(1L, "Hmm, I guess I should try again") }
    coVerify(exactly = 1) { mockRepository.addCommentToMergeRequest(1L, "I can't merge this merge request: it was not merged in 4h 30m. Assign me again to retry") }
    coVerify(exactly = 0) { mockRepository.merge(any()) }
    coVerify { mockRepository.assignMergeRequestTo(1L, emptyList()) }
  }

  @Test
  fun `merge should merge when target moves during a long pipeline and the rebased pipeline finishes`() = runTest(timeout = 5.seconds) {
    val user = createTestUser(id = 1L)
    var mergeRequestBaseSha = "target0"
    var pipelineStartedAt = 0L
    fun targetSha() = if (testScheduler.currentTime < 40.minutes.inWholeMilliseconds) "target0" else "target1"
    fun pipeline() = createTestPipeline(
      id = 1L,
      sha = "head456",
      status = if (testScheduler.currentTime - pipelineStartedAt >= 55.minutes.inWholeMilliseconds) {
        Pipeline.Status.SUCCESS
      } else {
        Pipeline.Status.RUNNING
      },
    )

    coEvery { mockRepository.getMergeRequest(1L) } answers {
      createTestMergeRequest(
        id = 1L,
        assignees = listOf(user),
        diffRefs = DiffRefs(baseSha = mergeRequestBaseSha, headSha = "head456"),
        pipeline = createTestPipeline(id = 1L, sha = "head456"),
      )
    }
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.getBranchInfo("main") } answers { createTestBranch(name = "main", commitSha = targetSha()) }
    coEvery { mockRepository.rebaseMergeRequest(1L) } answers {
      mergeRequestBaseSha = targetSha()
      pipelineStartedAt = testScheduler.currentTime
      RebaseResult(rebaseInProgress = false, mergeError = null)
    }
    coEvery { mockRepository.getPipeline(1L) } answers { pipeline() }
    var merged = false
    coEvery { mockRepository.merge(1L) } answers { merged = true }

    mergeHelper.merge(1L)

    assertThat(merged).isTrue()
    coVerify(exactly = 0) { mockRepository.addCommentToMergeRequest(1L, match { it.startsWith("I can't merge") }) }
    coVerify(exactly = 0) { mockRepository.assignMergeRequestTo(any(), any()) }
  }

  @Test
  fun `merge should not give up when the merge request is slower than every deadline`() = runTest(timeout = 5.seconds) {
    givenUpToDateMergeRequest(createTestPipeline(id = 1L, sha = "head456", status = Pipeline.Status.SUCCESS))
    coEvery { mockRepository.merge(1L) } coAnswers { delay(300.minutes) }

    mergeHelper.merge(1L)

    coVerify { mockRepository.merge(1L) }
    coVerify(exactly = 0) { mockRepository.addCommentToMergeRequest(any(), any()) }
    coVerify(exactly = 0) { mockRepository.assignMergeRequestTo(any(), any()) }
  }

  @Test
  fun `merge should give up when the merge request fails`() = runTest(timeout = 5.seconds) {
    givenUpToDateMergeRequest(createTestPipeline(id = 1L, sha = "head456", status = Pipeline.Status.SUCCESS))
    coEvery { mockRepository.merge(1L) } throws RuntimeException("405 Method Not Allowed")

    mergeHelper.merge(1L)

    coVerify(exactly = 1) { mockRepository.addCommentToMergeRequest(1L, "Something bad happened. Please check my logs") }
    coVerify { mockRepository.assignMergeRequestTo(1L, emptyList()) }
  }

  @Test
  fun `merge should post a single comment when the deadline fires while giving up`() = runTest(timeout = 5.seconds) {
    val user = createTestUser(id = 1L)
    val mergeRequest = createTestMergeRequest(id = 1L, draft = true, assignees = listOf(user))

    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.assignMergeRequestTo(any(), any()) } coAnswers { delay(300.minutes) }

    mergeHelper.merge(1L)

    coVerify(exactly = 1) { mockRepository.addCommentToMergeRequest(any(), any()) }
    coVerify(exactly = 1) { mockRepository.addCommentToMergeRequest(1L, "I can't merge this merge request: I can't merge drafts") }
  }

  private fun givenUpToDateMergeRequest(pipeline: Pipeline?) {
    val user = createTestUser(id = 1L)
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      assignees = listOf(user),
      diffRefs = DiffRefs(baseSha = "base123", headSha = "head456"),
      pipeline = pipeline,
    )
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.getBranchInfo("main") } returns createTestBranch(name = "main", commitSha = "base123")
    if (pipeline != null) coEvery { mockRepository.getPipeline(pipeline.id) } returns pipeline
  }

  private fun TestScope.thenGaveUpOnDeadline() {
    assertThat(testScheduler.currentTime).isEqualTo(90.minutes.inWholeMilliseconds)
    coVerify(exactly = 0) { mockRepository.merge(any()) }
    coVerify(exactly = 1) { mockRepository.addCommentToMergeRequest(any(), any()) }
    coVerify { mockRepository.addCommentToMergeRequest(1L, "I can't merge this merge request: an attempt took longer than 1h 30m. Assign me again to retry") }
    coVerify { mockRepository.assignMergeRequestTo(1L, emptyList()) }
  }
}
