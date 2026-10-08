package marge_kot.helpers

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import marge_kot.data.Repository
import marge_kot.data.dto.CannotMergeException
import marge_kot.data.dto.merge_request.DiffRefs
import marge_kot.data.dto.merge_request.RebaseResult
import marge_kot.test_utils.BaseTest
import marge_kot.test_utils.TestUtils.createTestBranch
import marge_kot.test_utils.TestUtils.createTestMergeRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RebaseHelperTest : BaseTest(), KoinComponent {

  private val rebaseHelper: RebaseHelper by inject()

  @BeforeEach
  override fun setUp() {
    super.setUp()
  }

  @Test
  fun `rebaseIfNeeded should return early when merge request is already up to date`() = runTest {
    val targetSha = "target123"
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = "main",
      diffRefs = DiffRefs(baseSha = targetSha, headSha = "head456"),
    )
    val branch = createTestBranch(name = "main", commitSha = targetSha)
    
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.getBranchInfo("main") } returns branch

    rebaseHelper.rebaseIfNeeded(1L)

    coVerify { mockRepository.getMergeRequest(1L) }
    coVerify { mockRepository.getBranchInfo("main") }
    coVerify(exactly = 0) { mockRepository.rebaseMergeRequest(any()) }
  }

  @Test
  fun `rebaseIfNeeded should rebase when merge request is not up to date`() = runTest {
    val targetSha = "target123"
    val oldBaseSha = "oldBase123"
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = "main",
      diffRefs = DiffRefs(baseSha = oldBaseSha, headSha = "head456"),
      rebaseInProgress = false,
    )
    val updatedMergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = "main",
      diffRefs = DiffRefs(baseSha = targetSha, headSha = "head456"),
    )
    val branch = createTestBranch(name = "main", commitSha = targetSha)
    val rebaseResult = RebaseResult(rebaseInProgress = false, mergeError = null)
    
    coEvery { mockRepository.getMergeRequest(1L) } returnsMany listOf(mergeRequest, updatedMergeRequest)
    coEvery { mockRepository.getBranchInfo("main") } returns branch
    coEvery { mockRepository.rebaseMergeRequest(1L) } returns rebaseResult

    rebaseHelper.rebaseIfNeeded(1L)

    coVerify(atLeast = 2) { mockRepository.getMergeRequest(1L) }
    coVerify { mockRepository.getBranchInfo("main") }
    coVerify { mockRepository.rebaseMergeRequest(1L) }
  }

  @Test
  fun `rebaseIfNeeded should wait when rebase is in progress`() = runTest {
    val targetSha = "target123"
    val oldBaseSha = "oldBase123"
    val mergeRequestInProgress = createTestMergeRequest(
      id = 1L,
      targetBranch = "main",
      diffRefs = DiffRefs(baseSha = oldBaseSha, headSha = "head456"),
      rebaseInProgress = true,
    )
    val mergeRequestDone = createTestMergeRequest(
      id = 1L,
      targetBranch = "main",
      diffRefs = DiffRefs(baseSha = targetSha, headSha = "head456"),
      rebaseInProgress = false,
    )
    val branch = createTestBranch(name = "main", commitSha = targetSha)
    
    coEvery { mockRepository.getMergeRequest(1L) } returnsMany listOf(
      mergeRequestInProgress,
      mergeRequestDone
    )
    coEvery { mockRepository.getBranchInfo("main") } returns branch

    rebaseHelper.rebaseIfNeeded(1L)

    coVerify(atLeast = 2) { mockRepository.getMergeRequest(1L) }
    coVerify { mockRepository.getBranchInfo("main") }
  }

  @Test
  fun `rebaseIfNeeded should throw CannotMergeException after 3 failed rebase attempts`() = runTest {
    val targetSha = "target123"
    val oldBaseSha = "oldBase123"
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = "main",
      diffRefs = DiffRefs(baseSha = oldBaseSha, headSha = "head456"),
      rebaseInProgress = false,
    )
    val branch = createTestBranch(name = "main", commitSha = targetSha)
    val rebaseResultWithError = RebaseResult(rebaseInProgress = false, mergeError = "Merge conflict")
    
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.getBranchInfo("main") } returns branch
    coEvery { mockRepository.rebaseMergeRequest(1L) } returns rebaseResultWithError

    val exception = assertThrows<CannotMergeException> {
      rebaseHelper.rebaseIfNeeded(1L)
    }

    assertThat(exception.message).contains("Failed to rebase")
    coVerify(atLeast = 3) { mockRepository.rebaseMergeRequest(1L) }
  }

  @Test
  fun `rebaseIfNeeded should throw CannotMergeException when rebase fails asynchronously`() = runTest {
    val targetSha = "target123"
    val oldBaseSha = "oldBase123"
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = "main",
      diffRefs = DiffRefs(baseSha = oldBaseSha, headSha = "head456"),
      rebaseInProgress = false,
    )
    val failedRebaseMergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = "main",
      diffRefs = DiffRefs(baseSha = oldBaseSha, headSha = "head456"),
      rebaseInProgress = false,
      mergeError = "Rebase failed: Rebase locally, resolve all conflicts, then push the branch.",
    )
    val branch = createTestBranch(name = "main", commitSha = targetSha)
    var rebaseCalls = 0

    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest andThen failedRebaseMergeRequest
    coEvery { mockRepository.getBranchInfo("main") } returns branch
    coEvery { mockRepository.rebaseMergeRequest(1L) } coAnswers {
      rebaseCalls++
      check(rebaseCalls <= 10) { "Rebase requested $rebaseCalls times without giving up" }
      RebaseResult(rebaseInProgress = true, mergeError = null)
    }

    val exception = assertThrows<CannotMergeException> {
      rebaseHelper.rebaseIfNeeded(1L)
    }

    assertThat(exception.message).contains("Failed to rebase")
    assertThat(rebaseCalls).isEqualTo(4)
  }

  @Test
  fun `rebaseIfNeeded should count failure once when error is in both rebase response and merge request`() = runTest {
    val failedRebaseMergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = "main",
      diffRefs = DiffRefs(baseSha = "oldBase123", headSha = "head456"),
      rebaseInProgress = false,
      mergeError = "Merge conflict",
    )
    val branch = createTestBranch(name = "main", commitSha = "target123")

    coEvery { mockRepository.getMergeRequest(1L) } returns failedRebaseMergeRequest
    coEvery { mockRepository.getBranchInfo("main") } returns branch
    coEvery { mockRepository.rebaseMergeRequest(1L) } returns RebaseResult(rebaseInProgress = false, mergeError = "Merge conflict")

    assertThrows<CannotMergeException> {
      rebaseHelper.rebaseIfNeeded(1L)
    }

    coVerify(exactly = 4) { mockRepository.rebaseMergeRequest(1L) }
  }

  @Test
  fun `rebaseIfNeeded should not count stale merge error before first rebase request`() = runTest {
    val targetSha = "target123"
    val oldBaseSha = "oldBase123"
    val staleErrorMergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = "main",
      diffRefs = DiffRefs(baseSha = oldBaseSha, headSha = "head456"),
      rebaseInProgress = false,
      mergeError = "Stale error from an earlier attempt",
    )
    val updatedMergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = "main",
      diffRefs = DiffRefs(baseSha = targetSha, headSha = "head789"),
      rebaseInProgress = false,
    )
    val branch = createTestBranch(name = "main", commitSha = targetSha)

    coEvery { mockRepository.getMergeRequest(1L) } returnsMany listOf(staleErrorMergeRequest, updatedMergeRequest)
    coEvery { mockRepository.getBranchInfo("main") } returns branch
    coEvery { mockRepository.rebaseMergeRequest(1L) } returns RebaseResult(rebaseInProgress = true, mergeError = null)

    rebaseHelper.rebaseIfNeeded(1L)

    coVerify(exactly = 1) { mockRepository.rebaseMergeRequest(1L) }
  }

  @Test
  fun `rebaseIfNeeded should retry on merge error`() = runTest {
    val targetSha = "target123"
    val oldBaseSha = "oldBase123"
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = "main",
      diffRefs = DiffRefs(baseSha = oldBaseSha, headSha = "head456"),
      rebaseInProgress = false,
    )
    val updatedMergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = "main",
      diffRefs = DiffRefs(baseSha = targetSha, headSha = "head456"),
      rebaseInProgress = false,
    )
    val branch = createTestBranch(name = "main", commitSha = targetSha)
    val rebaseResultWithError = RebaseResult(rebaseInProgress = false, mergeError = "Merge conflict")
    val rebaseResultSuccess = RebaseResult(rebaseInProgress = false, mergeError = null)
    
    coEvery { mockRepository.getMergeRequest(1L) } returnsMany listOf(
      mergeRequest, mergeRequest, updatedMergeRequest
    )
    coEvery { mockRepository.getBranchInfo("main") } returns branch
    coEvery { mockRepository.rebaseMergeRequest(1L) } returnsMany listOf(
      rebaseResultWithError,
      rebaseResultSuccess
    )

    rebaseHelper.rebaseIfNeeded(1L)

    coVerify(atLeast = 2) { mockRepository.rebaseMergeRequest(1L) }
  }

  @Test
  fun `rebaseIfNeeded should throw CannotMergeException when merge request has conflicts`() = runTest {
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      targetBranch = "main",
      hasConflicts = true,
      rebaseInProgress = false,
    )
    
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest

    val exception = assertThrows<CannotMergeException> {
      rebaseHelper.rebaseIfNeeded(1L)
    }

    assertThat(exception.message).contains("conflicts")
    coVerify { mockRepository.getMergeRequest(1L) }
    coVerify(exactly = 0) { mockRepository.rebaseMergeRequest(any()) }
  }
}

