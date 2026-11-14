package com.writingassistant.persistence.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.output.MigrateResult;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Enterprise database migration service powered by Flyway.
 * Manages versioned schema evolution across SQLite (desktop/local) and PostgreSQL (production).
 */
public class DatabaseMigrationService {

    private static final Logger LOGGER = Logger.getLogger(DatabaseMigrationService.class.getName());

    private final String jdbcUrl;
    private final DatabaseDialect dialect;
    private final Flyway flyway;

    public DatabaseMigrationService(String jdbcUrl) {
        this(jdbcUrl, null, null);
    }

    public DatabaseMigrationService(String jdbcUrl, String user, String password) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        this.dialect = DatabaseDialect.fromJdbcUrl(jdbcUrl);

        this.flyway = Flyway.configure()
                .dataSource(jdbcUrl, user, password)
                .locations(dialect.getMigrationLocation())
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .table("flyway_schema_history")
                .cleanDisabled(false) // Permitted for test environments
                .load();
    }

    public DatabaseMigrationService(DataSource dataSource, DatabaseDialect dialect) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect must not be null");
        this.jdbcUrl = "datasource-provided";

        this.flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(dialect.getMigrationLocation())
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .table("flyway_schema_history")
                .cleanDisabled(false)
                .load();
    }

    public DatabaseDialect getDialect() {
        return dialect;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    /**
     * Executes pending database migrations idempotently.
     *
     * @return Number of migrations executed.
     * @throws MigrationException if migration fails.
     */
    public int migrate() {
        try {
            LOGGER.info("Starting database migration for dialect: " + dialect + " on " + jdbcUrl);
            MigrateResult result = flyway.migrate();
            LOGGER.info("Database migration complete. Executed " + result.migrationsExecuted + " migrations. Target schema: " + result.targetSchemaVersion);
            return result.migrationsExecuted;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Database migration failed: " + e.getMessage(), e);
            throw new MigrationException("Database migration failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validates applied migrations against classpath migration scripts.
     *
     * @throws MigrationException if checksum or validation fails.
     */
    public void validate() {
        try {
            flyway.validate();
        } catch (Exception e) {
            throw new MigrationException("Database migration validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves current migration status and history.
     */
    public MigrationStatus getStatus() {
        MigrationInfoService infoService = flyway.info();
        MigrationInfo current = infoService.current();
        String currentVersion = current != null && current.getVersion() != null
                ? current.getVersion().getVersion()
                : "NONE";

        MigrationInfo[] all = infoService.all();
        List<MigrationInfoRecord> records = new ArrayList<>();
        int appliedCount = 0;
        int pendingCount = 0;

        for (MigrationInfo info : all) {
            String v = info.getVersion() != null ? info.getVersion().getVersion() : "N/A";
            String installedOn = info.getInstalledOn() != null ? info.getInstalledOn().toString() : "PENDING";
            String state = info.getState() != null ? info.getState().name() : "UNKNOWN";

            if (info.getState() != null && info.getState().isApplied()) {
                appliedCount++;
            } else {
                pendingCount++;
            }

            records.add(new MigrationInfoRecord(
                    v,
                    info.getDescription(),
                    state,
                    installedOn,
                    info.getScript()
            ));
        }

        boolean isUpToDate = pendingCount == 0 && appliedCount > 0;
        return new MigrationStatus(
                dialect,
                currentVersion,
                appliedCount,
                pendingCount,
                isUpToDate,
                records
        );
    }

    /**
     * Resets database schema (primarily intended for test environments).
     */
    public void clean() {
        try {
            flyway.clean();
        } catch (Exception e) {
            throw new MigrationException("Database clean failed: " + e.getMessage(), e);
        }
    }

    /**
     * Utility method for verifying schema health on application startup.
     */
    public static void verifyOrMigrate(String jdbcUrl) {
        DatabaseMigrationService service = new DatabaseMigrationService(jdbcUrl);
        service.migrate();
        service.validate();
    }
}
