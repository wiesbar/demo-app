package example

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DemoApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}
