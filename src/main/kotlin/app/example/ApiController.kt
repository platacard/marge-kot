package app.example

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.Thread.startVirtualThread

@RestController
class ApiController {

  @GetMapping("/hello")
  fun hello(name: String) {
    startVirtualThread {
      println("/hello $name")
    }
  }
}