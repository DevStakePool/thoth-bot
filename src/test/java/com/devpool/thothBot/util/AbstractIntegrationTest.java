package com.devpool.thothBot.util;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
public abstract class AbstractIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    private static final List<String> DB_MIGRATION = List.of(
            "db-migration/thoth-v1.0.0-rc1.sql",
            "db-migration/alter-1.0.0-rc1-TO-1.5.0.sql",
            "db-migration/alter-1.5.0-TO-1.7.0.sql",
            "db-migration/alter-1.7.0-TO-1.8.0.sql",
            "db-migration/alter-1.8.0-TO-1.9.1.sql",
            "db-migration/alter-1.9.1-TO-1.9.2.sql"
    );

    @Container
    private final static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.6")
            .withDatabaseName("testdb")
            .withUsername("postgres")
            .withPassword("testpass");

    @BeforeAll
    public static void setupPostgresContainer() throws IOException {
        LOG.info("Starting postgresql...");
        postgres.start();
        assertTrue(postgres.isRunning());
        LOG.info("Postgres started!");
        migrateDb();
    }

    private static void migrateDb() throws IOException {
        LOG.info("Migrating DB...");
        // Create a JDBC DataSource
        var dataSource = new SingleConnectionDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), true
        );
        var jdbcTemplate = new JdbcTemplate(dataSource);

        for (String sqlFile : DB_MIGRATION) {
            LOG.info("Executing {}", sqlFile);
            Path scriptPath = Paths.get(
                    Objects.requireNonNull(
                            AbstractIntegrationTest.class.getClassLoader()
                                    .getResource(sqlFile)).getPath());
            String sql = Files.readString(scriptPath);
            jdbcTemplate.execute(sql);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @AfterAll
    public static void shutdownPostgresContainer() {
        if (postgres != null)
            postgres.stop();
    }
}
