package marge_kot.di

import marge_kot.data.Repository
import marge_kot.helpers.LabelHandler
import marge_kot.helpers.MergeHelper
import marge_kot.helpers.MergeRequestMergeableChecker
import marge_kot.helpers.PipelineChecker
import marge_kot.helpers.RebaseHelper
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appModule = module {
  singleOf(::Repository)
  singleOf(::MergeRequestMergeableChecker)
  singleOf(::MergeHelper)
  singleOf(::LabelHandler)
  singleOf(::PipelineChecker)
  singleOf(::RebaseHelper)
}