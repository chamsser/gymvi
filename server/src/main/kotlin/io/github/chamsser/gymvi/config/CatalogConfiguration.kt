package io.github.chamsser.gymvi.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.chamsser.gymvi.catalog.FacilityCatalog
import io.github.chamsser.gymvi.catalog.JdbcFacilityCatalog
import io.github.chamsser.gymvi.catalog.UnavailableFacilityCatalog
import io.github.chamsser.gymvi.evidence.JdbcProgramEvidenceCatalog
import io.github.chamsser.gymvi.evidence.ProgramEvidenceCatalog
import io.github.chamsser.gymvi.evidence.UnavailableProgramEvidenceCatalog
import io.github.chamsser.gymvi.meta.ActiveDatasetCatalog
import io.github.chamsser.gymvi.meta.JdbcActiveDatasetCatalog
import io.github.chamsser.gymvi.meta.UnavailableActiveDatasetCatalog
import io.github.chamsser.gymvi.usage.JdbcUsageOptionCatalog
import io.github.chamsser.gymvi.usage.UnavailableUsageOptionCatalog
import io.github.chamsser.gymvi.usage.UsageOptionCatalog
import io.github.chamsser.gymvi.usage.JdbcUsageOptionQueryCatalog
import io.github.chamsser.gymvi.usage.UnavailableUsageOptionQueryCatalog
import io.github.chamsser.gymvi.usage.UsageOptionQueryCatalog
import org.flywaydb.core.Flyway
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import tools.jackson.databind.ObjectMapper
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@Profile("!postgis")
class DefaultCatalogConfiguration {
    @Bean
    fun facilityCatalog(): FacilityCatalog = UnavailableFacilityCatalog()

    @Bean
    fun usageOptionCatalog(): UsageOptionCatalog = UnavailableUsageOptionCatalog()

    @Bean
    fun programEvidenceCatalog(): ProgramEvidenceCatalog = UnavailableProgramEvidenceCatalog()

    @Bean
    fun usageOptionQueryCatalog(): UsageOptionQueryCatalog = UnavailableUsageOptionQueryCatalog()

    @Bean
    fun activeDatasetCatalog(): ActiveDatasetCatalog = UnavailableActiveDatasetCatalog()
}

@Configuration(proxyBeanMethods = false)
@Profile("postgis")
class PostgisCatalogConfiguration(
    @param:Value("\${GYMVI_DB_URL}") private val databaseUrl: String,
    @param:Value("\${GYMVI_DB_USERNAME}") private val databaseUsername: String,
    @param:Value("\${GYMVI_DB_PASSWORD}") private val databasePassword: String,
) {
    @Bean(destroyMethod = "close")
    fun dataSource(): HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = databaseUrl
                username = databaseUsername
                password = databasePassword
                maximumPoolSize = 4
                minimumIdle = 0
                connectionTimeout = 5_000
                validationTimeout = 2_000
                poolName = "gymvi-postgis"
            },
        )

    @Bean
    fun flyway(dataSource: DataSource): Flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .cleanDisabled(true)
            .validateMigrationNaming(true)
            .load()
            .also { it.migrate() }

    @Bean
    fun facilityCatalog(dataSource: DataSource, flyway: Flyway): FacilityCatalog {
        check(flyway.info().current() != null) { "PostGIS schema migration did not complete." }
        return JdbcFacilityCatalog(JdbcTemplate(dataSource))
    }

    @Bean
    fun usageOptionCatalog(dataSource: DataSource, flyway: Flyway): UsageOptionCatalog {
        check(flyway.info().current() != null) { "PostGIS schema migration did not complete." }
        return JdbcUsageOptionCatalog(JdbcTemplate(dataSource))
    }

    @Bean
    fun programEvidenceCatalog(
        dataSource: DataSource,
        flyway: Flyway,
        objectMapper: ObjectMapper,
    ): ProgramEvidenceCatalog {
        check(flyway.info().current() != null) { "PostGIS schema migration did not complete." }
        return JdbcProgramEvidenceCatalog(JdbcTemplate(dataSource), objectMapper)
    }

    @Bean
    fun usageOptionQueryCatalog(dataSource: DataSource, flyway: Flyway): UsageOptionQueryCatalog {
        check(flyway.info().current() != null) { "PostGIS schema migration did not complete." }
        return JdbcUsageOptionQueryCatalog(JdbcTemplate(dataSource))
    }

    @Bean
    fun activeDatasetCatalog(dataSource: DataSource, flyway: Flyway): ActiveDatasetCatalog {
        check(flyway.info().current() != null) { "PostGIS schema migration did not complete." }
        return JdbcActiveDatasetCatalog(JdbcTemplate(dataSource))
    }
}
