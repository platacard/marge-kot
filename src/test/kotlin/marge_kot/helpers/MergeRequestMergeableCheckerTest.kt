package marge_kot.helpers

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import marge_kot.data.Repository
import marge_kot.data.dto.CannotMergeException
import marge_kot.data.dto.merge_request.MergeStatus
import marge_kot.helpers.checkForNoConflicts
import marge_kot.test_utils.BaseTest
import marge_kot.test_utils.TestUtils.createTestMergeRequest
import marge_kot.test_utils.TestUtils.createTestMergeRequestApprovals
import marge_kot.test_utils.TestUtils.createTestUser
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MergeRequestMergeableCheckerTest : BaseTest(), KoinComponent {

  private val checker: MergeRequestMergeableChecker by inject()

  @BeforeEach
  override fun setUp() {
    super.setUp()
  }

  @Test
  fun `check should pass when merge request is mergeable`() = runTest {
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      mergeStatus = MergeStatus.CAN_BE_MERGED,
      blockingDiscussionsResolved = true,
      hasConflicts = false,
    )
    val user = createTestUser(id = 1L)
    
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true
    coEvery { mockRepository.getUserInfo() } returns user

    checker.check(assignCheckIsNeeded = true, mergeRequestId = 1L)

    coVerify { mockRepository.getMergeRequest(1L) }
    coVerify { mockRepository.checkIfMergeRequestApproved(1L) }
    coVerify { mockRepository.getUserInfo() }
  }

  @Test
  fun `check should throw CannotMergeException when merge request is already merged`() = runTest {
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      mergeStatus = MergeStatus.MERGED,
    )
    
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest

    val exception = assertThrows<CannotMergeException> {
      checker.check(assignCheckIsNeeded = false, mergeRequestId = 1L)
    }

    assertThat(exception.message).contains("merged already")
    coVerify { mockRepository.getMergeRequest(1L) }
    coVerify(exactly = 0) { mockRepository.checkIfMergeRequestApproved(any()) }
  }

  @Test
  fun `check should throw CannotMergeException when merge request is draft`() = runTest {
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      draft = true,
    )
    
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest

    val exception = assertThrows<CannotMergeException> {
      checker.check(assignCheckIsNeeded = false, mergeRequestId = 1L)
    }

    assertThat(exception.message).contains("drafts")
    coVerify { mockRepository.getMergeRequest(1L) }
  }

  @Test
  fun `check should throw CannotMergeException when merge request is not approved`() = runTest {
    val mergeRequest = createTestMergeRequest(id = 1L)
    
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns false

    val exception = assertThrows<CannotMergeException> {
      checker.check(assignCheckIsNeeded = false, mergeRequestId = 1L)
    }

    assertThat(exception.message).contains("Insufficient approves")
    coVerify { mockRepository.getMergeRequest(1L) }
    coVerify { mockRepository.checkIfMergeRequestApproved(1L) }
  }

  @Test
  fun `check should throw CannotMergeException when blocking discussions are not resolved`() = runTest {
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      blockingDiscussionsResolved = false,
    )
    
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true

    val exception = assertThrows<CannotMergeException> {
      checker.check(assignCheckIsNeeded = false, mergeRequestId = 1L)
    }

    assertThat(exception.message).contains("Blocking discussions")
    coVerify { mockRepository.getMergeRequest(1L) }
    coVerify { mockRepository.checkIfMergeRequestApproved(1L) }
  }

  @Test
  fun `check should throw CannotMergeException when merge request has conflicts`() = runTest {
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      hasConflicts = true,
    )
    
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true

    val exception = assertThrows<CannotMergeException> {
      checker.check(assignCheckIsNeeded = false, mergeRequestId = 1L)
    }

    assertThat(exception.message).contains("conflicts")
    coVerify { mockRepository.getMergeRequest(1L) }
    coVerify { mockRepository.checkIfMergeRequestApproved(1L) }
  }

  @Test
  fun `check should throw CannotMergeException when bot is not assigned when assignCheckIsNeeded is true`() = runTest {
    val user = createTestUser(id = 1L)
    val otherUser = createTestUser(id = 2L, name = "Other User", username = "other")
    val mergeRequest = createTestMergeRequest(
      id = 1L,
      assignees = listOf(otherUser), // Bot is not assigned
    )
    
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true
    coEvery { mockRepository.getUserInfo() } returns user

    val exception = assertThrows<CannotMergeException> {
      checker.check(assignCheckIsNeeded = true, mergeRequestId = 1L)
    }

    assertThat(exception.message).contains("unassigned")
    coVerify { mockRepository.getMergeRequest(1L) }
    coVerify { mockRepository.checkIfMergeRequestApproved(1L) }
    coVerify { mockRepository.getUserInfo() }
  }

  @Test
  fun `check should not check assignment when assignCheckIsNeeded is false`() = runTest {
    val mergeRequest = createTestMergeRequest(id = 1L)
    
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true

    checker.check(assignCheckIsNeeded = false, mergeRequestId = 1L)

    coVerify { mockRepository.getMergeRequest(1L) }
    coVerify { mockRepository.checkIfMergeRequestApproved(1L) }
    coVerify(exactly = 0) { mockRepository.getUserInfo() }
  }

  @Test
  fun `checkForNoConflicts extension should throw CannotMergeException when hasConflicts is true`() = runTest {
    val mergeRequest = createTestMergeRequest(hasConflicts = true)
    
    assertThrows<CannotMergeException> {
      mergeRequest.checkForNoConflicts()
    }
  }

  @Test
  fun `checkForNoConflicts extension should not throw when hasConflicts is false`() = runTest {
    val mergeRequest = createTestMergeRequest(hasConflicts = false)
    
    // Should not throw
    mergeRequest.checkForNoConflicts()
  }
}

