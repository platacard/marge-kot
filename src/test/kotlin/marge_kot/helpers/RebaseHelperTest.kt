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

