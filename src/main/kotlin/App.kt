import kotlinx.coroutines.delay
import marge_kot.executor.Executor

suspend fun main() {
  println("delay")
  delay(3000)
  println("Zdarova banditi tururutu tururuturuuuu")
  Executor.runBot()
}