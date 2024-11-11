package marge_kot.helpers

import io.github.aakira.napier.Napier
import io.ktor.client.plugins.ServerResponseException
import marge_kot.data.Repository
import marge_kot.data.dto.NeedRebaseException
import marge_kot.data.dto.RebaseErrorException
import marge_kot.data.dto.merge_request.MergeRequest

class MergeHelper(
  val repository: Repository,
) {

  suspend fun merge(mergeRequest: MergeRequest) {
    val mergeRequestId = mergeRequest.id
    val mergeableChecker = MergeRequestMergeableChecker(repository, mergeRequestId)
    val rebaseHelper = RebaseHelper(repository, mergeRequestId)
    val pipelineWaiter = PipelineWaiter(repository, mergeRequestId)
    while (true) {
      try {
        mergeableChecker.check()
        rebaseHelper.rebaseIfNeeded()
        pipelineWaiter.waitForPipeline()
        mergeableChecker.check()
        repository.merge(mergeRequestId)
      } catch (rebaseEx: RebaseErrorException) {
        Napier.i("Rebase error: $rebaseEx")
        unassignBot(repository, mergeRequestId)
        return
      } catch (serverResponseEx: ServerResponseException) {
        Napier.i("Something happened with Gitlab: $serverResponseEx")
        unassignBot(repository, mergeRequestId)
        return
      } catch (needRebaseEx: NeedRebaseException) {
        Napier.i("Need rebase again")
        continue
      } catch (ex: Throwable) {
        Napier.i("Unhandled exception: $ex")
        unassignBot(repository, mergeRequestId)
        return
      }
    }
  }
}