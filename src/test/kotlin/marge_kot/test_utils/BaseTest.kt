package marge_kot.test_utils

import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import io.mockk.mockk
import marge_kot.data.Repository
import marge_kot.helpers.LabelHandler
import marge_kot.helpers.MergeHelper
import marge_kot.helpers.MergeRequestMergeableChecker
import marge_kot.helpers.PipelineChecker
import marge_kot.helpers.RebaseHelper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import kotlin.time.Duration.Companion.minutes

abstract class BaseTest {

  protected lateinit var mockRepository: Repository

  @BeforeEach
  open fun setUp() {
    mockRepository = mockk<Repository>(relaxed = true)
    
    val testModule = module {
      single<Repository> { mockRepository }
      singleOf(::MergeRequestMergeableChecker)
      single {
        MergeHelper(
          repository = get(),
          mergeableChecker = get(),
          rebaseHelper = get(),
          pipelineChecker = get(),
          mergeTimeout = 90.minutes,
        )
      }
      single {
        LabelHandler(
          repository = get(),
          mergeableChecker = get(),
          pipelineChecker = get(),
          label = "test-auto-merge", // Test label for testing purposes
        )
      }
      singleOf(::PipelineChecker)
      singleOf(::RebaseHelper)
    }

    startKoin {
      modules(testModule)
    }
  }

  @AfterEach
  open fun tearDown() {
    stopKoin()
  }
}

