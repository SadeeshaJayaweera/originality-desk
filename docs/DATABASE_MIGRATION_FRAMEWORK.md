# Database Migration Framework Architecture

## 1. Executive Summary and Technology Selection

Originality Desk transitions from manual SQL script execution to an enterprise, versioned database migration framework powered by **Flyway 9.22.3**.

### Framework Evaluation: Flyway vs. Liquibase

| Criteria | Flyway | Liquibase |
|---|---|---|
| **Syntax** | Pure SQL (`.sql`) scripts matching native database dialects | XML, YAML, JSON, or formatted SQL |
| **Footprint** | Lightweight jar (~3 MB), zero additional runtime dependencies | Heavier footprint with XML parsers and custom changelog handlers |
| **Dialect Support** | Native out-of-the-box support for SQLite and PostgreSQL | Requires complex extensions for SQLite WAL modes |
| **Developer Ergonomics** | Standard `V{version}__{description}.sql` naming conventions | Complex changeset tagging and precondition clauses |
| **Verification** | Cryptographic SHA-256 checksums per migration file | Checksum-based with XML diff overhead |

**Decision**: Flyway was selected for its simplicity, pure SQL ergonomics, zero-dependency SQLite and PostgreSQL compatibility, and robust checksum validation.

---

## 2. Directory Structure and Dialect Isolation

To ensure reliable operation across local SQLite environments (desktop app and single-node development) and target PostgreSQL clusters (production enterprise deployments), migrations are separated by dialect:

```
src/main/resources/db/migration/
├── sqlite/
│   └── V1__initial_schema.sql      # SQLite-optimized DDL (WAL-compatible, TEXT/INTEGER types)
└── postgresql/
    └── V1__initial_schema.sql      # PostgreSQL-optimized DDL (BIGSERIAL, VARCHAR, TIMESTAMPTZ, foreign keys)
```

---

## 3. Component Architecture

Located under `com.writingassistant.persistence.migration`:

```
com.writingassistant.persistence.migration
├── DatabaseDialect.java             # Resolves SQLITE or POSTGRESQL from JDBC URL
├── MigrationInfoRecord.java         # Immutable snapshot of an applied or pending migration
├── MigrationStatus.java             # Consolidated schema status (version, applied count, up-to-date flag)
├── MigrationException.java          # Typed exception for migration, checksum, or validation failures
└── DatabaseMigrationService.java    # Flyway runner, validator, and startup verification utility
```

### 3.1 Dialect Detection (`DatabaseDialect`)

```java
public static DatabaseDialect fromJdbcUrl(String jdbcUrl) {
    if (jdbcUrl.startsWith("jdbc:sqlite:")) return SQLITE;
    if (jdbcUrl.startsWith("jdbc:postgresql:") || jdbcUrl.startsWith("jdbc:postgres:")) return POSTGRESQL;
    throw new IllegalArgumentException("Unsupported dialect: " + jdbcUrl);
}
```

### 3.2 Idempotent Migration Execution (`DatabaseMigrationService`)

Configured with:
- `locations(dialect.getMigrationLocation())`
- `baselineOnMigrate(true)` (enables graceful baseline for pre-existing databases)
- `baselineVersion("0")`
- `table("flyway_schema_history")`

```java
DatabaseMigrationService service = new DatabaseMigrationService(jdbcUrl);
int executed = service.migrate();
service.validate();
```

---

## 4. Dual-Dialect Schema Equivalency

| Entity | SQLite Implementation | PostgreSQL Implementation |
|---|---|---|
| Auto-increment PK | `id INTEGER PRIMARY KEY AUTOINCREMENT` | `id BIGSERIAL PRIMARY KEY` |
| Variable text | `TEXT` | `VARCHAR(255)` / `TEXT` |
| Timestamps | `created_at TEXT NOT NULL` | `created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP` |
| Epoch millis | `expires_at INTEGER NOT NULL` | `expires_at BIGINT NOT NULL` |
| Storage hash | `sha256 TEXT NOT NULL` | `sha256 VARCHAR(64) NOT NULL` |

---

## 5. Verification and Test Suite

Verified via `FlywayMigrationTest`:
1. `dialectResolution`: Confirms automatic dialect mapping from SQLite and PostgreSQL JDBC URLs.
2. `cleanDatabaseMigration`: Applies V1 migration to empty SQLite database, verifying all 10 core tables and `flyway_schema_history`.
3. `migrationIdempotency`: Executes consecutive migrations, ensuring the second execution applies 0 migrations and leaves the schema unmodified.
4. `postgresqlMigrationScriptSyntax`: Inspects PostgreSQL migration resource, verifying valid DDL syntax, sequence types, and timestamp constraints.
5. `verifyOrMigrateUtility`: Verifies startup helper safely applies and validates migrations without error.
