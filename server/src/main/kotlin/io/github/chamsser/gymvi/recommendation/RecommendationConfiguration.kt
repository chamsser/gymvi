package io.github.chamsser.gymvi.recommendation

import io.github.chamsser.gymvi.usage.UsageOptionQueryCatalog
import io.github.chamsser.gymvi.usage.OperatorProgramTimeCatalog
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration(proxyBeanMethods = false)
class RecommendationConfiguration {
    @Bean
    fun recommendationService(
        catalog: UsageOptionQueryCatalog,
        operatorProgramTimeCatalog: OperatorProgramTimeCatalog,
    ): RecommendationService = RecommendationService(
        catalog,
        Clock.system(ZoneId.of("Asia/Seoul")),
        operatorProgramTimeCatalog = operatorProgramTimeCatalog,
    )
}
