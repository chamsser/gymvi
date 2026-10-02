package io.github.chamsser.gymvi.ai

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration(proxyBeanMethods = false)
internal class AiStreamingConfiguration {
    @Bean
    fun aiStreamExecutor() = ThreadPoolTaskExecutor().apply {
        corePoolSize = 4
        maxPoolSize = 8
        queueCapacity = 16
        setThreadNamePrefix("gymvi-ai-stream-")
    }

    @Bean
    fun aiStreamingMvcConfigurer(@Qualifier("aiStreamExecutor") executor: AsyncTaskExecutor) =
        object : WebMvcConfigurer {
            override fun configureAsyncSupport(configurer: AsyncSupportConfigurer) {
                // Four upstream calls are each limited to 18 seconds. Leave room for
                // bounded database/tool work without the default MVC 30-second cutoff.
                configurer.setTaskExecutor(executor).setDefaultTimeout(120_000)
            }
        }
}
