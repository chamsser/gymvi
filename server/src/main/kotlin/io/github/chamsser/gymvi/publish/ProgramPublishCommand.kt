package io.github.chamsser.gymvi.publish

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import java.nio.file.Path

fun main(args: Array<String>) {
    require(args.size % 2 == 0) { "Arguments must be option/value pairs." }
    val arguments = args.toList().chunked(2).associate { (name, value) -> name to value }
    val required = setOf("--metadata", "--normalized", "--qa-report", "--joins", "--join-report")
    require(arguments.keys == required) { "Required arguments: ${required.sorted().joinToString(" ")}" }

    HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = programEnvironment("GYMVI_DB_URL")
            username = programEnvironment("GYMVI_DB_USERNAME")
            password = programEnvironment("GYMVI_DB_PASSWORD")
            maximumPoolSize = 2
            minimumIdle = 0
            poolName = "gymvi-program-publisher"
        },
    ).use { dataSource ->
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .cleanDisabled(true)
            .validateMigrationNaming(true)
            .load()
            .migrate()

        val receipt = ProgramPublisher(
            DataSourceTransactionManager(dataSource),
            JdbcTemplate(dataSource),
        ).publish(
            ProgramPublishFiles(
                metadata = Path.of(arguments.getValue("--metadata")),
                normalized = Path.of(arguments.getValue("--normalized")),
                qaReport = Path.of(arguments.getValue("--qa-report")),
                joins = Path.of(arguments.getValue("--joins")),
                joinReport = Path.of(arguments.getValue("--join-report")),
            ),
        )
        println(
            "Published program_dataset_version=${receipt.datasetVersion} " +
                "facility_dataset_version=${receipt.facilityDatasetVersion} " +
                "program_count=${receipt.programCount} facility_count=${receipt.facilityCount} " +
                "as_of=${receipt.activeAsOf}",
        )
    }
}

private fun programEnvironment(name: String): String =
    System.getenv(name)?.takeIf(String::isNotBlank)
        ?: error("Required environment variable is missing: $name")
