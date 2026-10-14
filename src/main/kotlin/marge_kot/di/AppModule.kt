package marge_kot.di

import marge_kot.data.Repository
import marge_kot.helpers.LabelHandler
import marge_kot.helpers.MERGE_CEILING_ATTEMPTS
import marge_kot.helpers.MergeHelper
import marge_kot.helpers.MergeRequestMergeableChecker
import marge_kot.helpers.PipelineChecker
import marge_kot.helpers.RebaseHelper
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

val appModule = module {
  singleOf(::Repository)
  singleOf(::MergeRequestMergeableChecker)
  single(createdAtStart = true) {
    MergeHelper(
      repository = get(),
      mergeableChecker = get(),
      rebaseHelper = get(),
      pipelineChecker = get(),
      mergeTimeout = parseMergeTimeout(System.getenv("MARGE_KOT_MERGE_TIMEOUT_MINUTES")),
    )
  }
  single {
    LabelHandler(
      repository = get(),
      mergeableChecker = get(),
      pipelineChecker = get(),
      label = System.getenv("MARGE_KOT_AUTO_MERGE_LABEL"),
    )
  }
  singleOf(::PipelineChecker)
  singleOf(::RebaseHelper)
}
internal fun parseMergeTimeout(minutes: String?): Duration {
  if (minutes == null) return 90.minutes
  val value = minutes.toLongOrNull()
  val timeout = value?.minutes
  if (timeout == null || !timeout.isPositive() || !(timeout * MERGE_CEILING_ATTEMPTS).isFinite()) {
    error("MARGE_KOT_MERGE_TIMEOUT_MINUTES must be a positive finite number of minutes, got '$minutes'")
  }
  return timeout
}
