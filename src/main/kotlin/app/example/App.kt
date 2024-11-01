package app.example

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor

@EnableScheduling
@SpringBootConfiguration
@EnableAutoConfiguration
class App {

  @Bean
  fun controller(): ApiController {
    return ApiController()
  }
}

fun main(args: Array<String>) {
  runApplication<App>(*args)
}
