package marge_kot.di

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.time.Duration.Companion.minutes

class MergeTimeoutTest {

  @Test
  fun `parseMergeTimeout should default to 90 minutes when not set`() {
    assertThat(parseMergeTimeout(null)).isEqualTo(90.minutes)
  }

  @ParameterizedTest
  @ValueSource(strings = ["0", "-5", "90m"])
  fun `parseMergeTimeout should reject value that is not a positive number of minutes`(value: String) {
    assertThrows<IllegalStateException> { parseMergeTimeout(value) }
  }
}
