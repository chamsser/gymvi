package io.github.chamsser.gymvi.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration(proxyBeanMethods = false)
class ClockConfiguration {
    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))
}
