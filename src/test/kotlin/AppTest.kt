import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import marge_kot.data.dto.common.Scope
import marge_kot.test_utils.BaseTest
import marge_kot.test_utils.TestUtils.createTestMergeRequest
import marge_kot.test_utils.TestUtils.createTestUser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AppTest : BaseTest() {

  @Test
  fun `runIteration should survive an error while auto-assigning labeled merge requests`() = runTest {
    val mergeRequest = createTestMergeRequest(id = 1L, labels = listOf("test-auto-merge"))
    coEvery {
      mockRepository.getOpenedMergeRequests(scope = Scope.ALL, targetBranch = "main", label = "test-auto-merge")
    } returns listOf(mergeRequest)
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.checkIfMergeRequestApproved(1L) } returns true
    coEvery { mockRepository.getUserInfo() } throws RuntimeException("GitLab is down")

    App().runIteration("main")

    assertThat(testScheduler.currentTime).isEqualTo(30_000L)
  }

  @Test
  fun `runIteration should propagate cancellation`() = runTest {
    coEvery {
      mockRepository.getOpenedMergeRequests(scope = Scope.ALL, targetBranch = "main", label = "test-auto-merge")
    } throws CancellationException("Shutdown")

    assertThrows<CancellationException> { App().runIteration("main") }
  }

  @Test
  fun `runIteration should back off when the bot cannot be unassigned from a merge request it gave up on`() = runTest {
    val user = createTestUser(id = 1L)
    val mergeRequest = createTestMergeRequest(id = 1L, draft = true, assignees = listOf(user))
    coEvery {
      mockRepository.getOpenedMergeRequests(scope = Scope.ASSIGNED_TO_ME, targetBranch = "main", label = null)
    } returns listOf(mergeRequest)
    coEvery { mockRepository.getMergeRequest(1L) } returns mergeRequest
    coEvery { mockRepository.getUserInfo() } returns user
    coEvery { mockRepository.assignMergeRequestTo(any(), any()) } throws RuntimeException("GitLab is down")

    App().runIteration("main")

    assertThat(testScheduler.currentTime).isEqualTo(30_000L)
  }
}
