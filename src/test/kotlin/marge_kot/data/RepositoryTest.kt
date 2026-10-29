package marge_kot.data

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import marge_kot.data.dto.common.Scope
import org.junit.jupiter.api.Test

class RepositoryTest {

  private val requestedPages = mutableListOf<Int>()
  private val requestedPageSizes = mutableListOf<String?>()

  @Test
  fun `getOpenedMergeRequests returns merge requests from every page`() = runTest {
    val repository = repositoryWithGitlab(totalMergeRequests = 150)

    val mergeRequests = repository.getOpenedMergeRequests("main", Scope.ALL, "auto-merge")

    assertThat(mergeRequests.map { it.id }).containsExactlyElementsIn(1L..150L).inOrder()
  }

  @Test
  fun `getOpenedMergeRequests fetches a list that fits one page with a single request`() = runTest {
    val repository = repositoryWithGitlab(totalMergeRequests = 34)

    val mergeRequests = repository.getOpenedMergeRequests("main", Scope.ALL, "auto-merge")

    assertThat(mergeRequests).hasSize(34)
    assertThat(requestedPages).containsExactly(1)
    assertThat(requestedPageSizes).containsExactly("100")
  }

  @Test
  fun `getOpenedMergeRequests returns nothing when a later page fails`() = runTest {
    val repository = repositoryWithGitlab(totalMergeRequests = 150, failingPage = 2)

    val mergeRequests = repository.getOpenedMergeRequests("main", Scope.ALL, "auto-merge")

    assertThat(mergeRequests).isEmpty()
  }

  private fun repositoryWithGitlab(totalMergeRequests: Int, failingPage: Int? = null): Repository {
    val engine = MockEngine { request ->
      respondPage(request, totalMergeRequests, failingPage)
    }
    return Repository(
      projectId = "1",
      client = createClient(engine = engine, baseUrl = "https://gitlab.test/api/v4/", authToken = "token"),
    )
  }

  private fun MockRequestHandleScope.respondPage(
    request: HttpRequestData,
    totalMergeRequests: Int,
    failingPage: Int?,
  ) = run {
    val page = request.url.parameters["page"]?.toInt() ?: 1
    val perPage = request.url.parameters["per_page"]?.toInt()?.coerceAtMost(100) ?: 20
    requestedPages += page
    requestedPageSizes += request.url.parameters["per_page"]
    if (page == failingPage) return@run respond("", HttpStatusCode.Forbidden)
    val ids = (1..totalMergeRequests).drop((page - 1) * perPage).take(perPage)
    val nextPage = if (page * perPage < totalMergeRequests) (page + 1).toString() else ""
    respond(
      content = ids.joinToString(prefix = "[", postfix = "]") { mergeRequestJson(it) },
      headers = headersOf(
        HttpHeaders.ContentType to listOf("application/json"),
        "x-next-page" to listOf(nextPage),
        "x-page" to listOf(page.toString()),
        "x-per-page" to listOf(perPage.toString()),
      ),
    )
  }

  private fun mergeRequestJson(id: Int) = """
    {"iid":$id,"title":"MR $id","state":"opened","target_branch":"main","source_branch":"feature-$id",
    "assignees":[],"reviewers":[],"draft":false,"blocking_discussions_resolved":true,"labels":["auto-merge"]}
  """.trimIndent()
}
