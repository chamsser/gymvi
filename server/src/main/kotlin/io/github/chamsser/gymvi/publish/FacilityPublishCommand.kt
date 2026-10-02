package io.github.chamsser.gymvi.publish

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import java.nio.file.Path

fun main(args: Array<String>) {
    val arguments = parseArguments(args)
    val databaseUrl = requiredEnvironment("GYMVI_DB_URL")
    val databaseUsername = requiredEnvironment("GYMVI_DB_USERNAME")
    val databasePassword = requiredEnvironment("GYMVI_DB_PASSWORD")

    HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = databaseUrl
            username = databaseUsername
            password = databasePassword
            maximumPoolSize = 2
            minimumIdle = 0
            poolName = "gymvi-publisher"
        },
    ).use { dataSource ->
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .cleanDisabled(true)
            .validateMigrationNaming(true)
            .load()
            .migrate()

        val receipt = FacilityPublisher(
            DataSourceTransactionManager(dataSource),
            JdbcTemplate(dataSource),
        ).publish(
            FacilityPublishFiles(
                metadata = Path.of(arguments.getValue("--metadata")),
                normalized = Path.of(arguments.getValue("--normalized")),
                qaReport = Path.of(arguments.getValue("--qa-report")),
            ),
        )
        println(
            "Published dataset_version=${receipt.datasetVersion} " +
                "facility_count=${receipt.facilityCount} as_of=${receipt.activeAsOf}",
        )
    }
}

private fun parseArguments(args: Array<String>): Map<String, String> {
    require(args.size % 2 == 0) { "Arguments must be option/value pairs." }
    val parsed = args.toList().chunked(2).associate { (name, value) -> name to value }
    val required = setOf("--metadata", "--normalized", "--qa-report")
    require(parsed.keys == required) { "Required arguments: ${required.sorted().joinToString(" ")}" }
    return parsed
}

private fun requiredEnvironment(name: String): String =
    System.getenv(name)?.takeIf(String::isNotBlank)
        ?: error("Required environment variable is missing: $name")
