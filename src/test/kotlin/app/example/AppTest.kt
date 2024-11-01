package app.example

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

@SpringBootTest
class AppTest {

  @Autowired
  private lateinit var appContext: ApplicationContext

  @Test
  fun testContext() {
    assertThat(appContext).isNotNull()
  }
}