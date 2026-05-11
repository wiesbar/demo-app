package example.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {
    @GetMapping("/")
    @Suppress("FunctionOnlyReturningConstant")
    fun index(): String = "The Demo Service is running!"
}
