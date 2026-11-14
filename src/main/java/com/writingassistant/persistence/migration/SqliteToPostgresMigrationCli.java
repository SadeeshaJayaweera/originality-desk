package com.writingassistant.persistence.migration;

import java.util.Map;

/**
 * Command-line interface for institutional database administrators executing
 * the SQLite-to-PostgreSQL migration with automated validation.
 */
public class SqliteToPostgresMigrationCli {

    public static void main(String[] args) {
        String source = null;
        String target = null;
        String user = null;
        String password = null;
        boolean dryRun = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--source" -> { if (++i < args.length) source = args[i]; }
                case "--target" -> { if (++i < args.length) target = args[i]; }
                case "--user" -> { if (++i < args.length) user = args[i]; }
                case "--password" -> { if (++i < args.length) password = args[i]; }
                case "--dry-run" -> dryRun = true;
                case "--help" -> {
                    printUsage();
                    System.exit(0);
                }
            }
        }

        if (source == null || target == null) {
            System.err.println("Error: Both --source and --target parameters are required.");
            printUsage();
            System.exit(1);
        }

        if (!source.startsWith("jdbc:sqlite:")) {
            source = "jdbc:sqlite:" + source;
        }

        System.out.println("==========================================================");
        System.out.println("  Originality Desk — SQLite to PostgreSQL Migration CLI   ");
        System.out.println("==========================================================");
        System.out.println("Source:  " + source);
        System.out.println("Target:  " + target);
        System.out.println("Dry-run: " + dryRun);
        System.out.println("----------------------------------------------------------");

        try {
            SqliteToPostgresMigrator migrator = new SqliteToPostgresMigrator(source, target, user, password);
            migrator.setDryRun(dryRun);

            MigrationValidationReport report = migrator.migrate();

            System.out.println("\nExecution Audit Trail:");
            for (String msg : report.auditMessages()) {
                System.out.println("  * " + msg);
            }

            System.out.println("\nTable Transfer Summary:");
            for (Map.Entry<String, Long> entry : report.sourceRowCounts().entrySet()) {
                long targetCount = report.targetRowCounts().getOrDefault(entry.getKey(), 0L);
                System.out.printf("  %-20s : %d source rows -> %d target rows%n", entry.getKey(), entry.getValue(), targetCount);
            }

            System.out.println("----------------------------------------------------------");
            System.out.println("Status:   " + report.status());
            System.out.println("Duration: " + report.durationMillis() + " ms");

            if (!report.integrityViolations().isEmpty()) {
                System.err.println("\nINTEGRITY VIOLATIONS DETECTED:");
                for (String v : report.integrityViolations()) {
                    System.err.println("  [!] " + v);
                }
                System.exit(1);
            }

            System.out.println("\nMigration completed successfully with zero integrity violations.");
            System.exit(0);
        } catch (Exception e) {
            System.err.println("\nFATAL MIGRATION ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java com.writingassistant.persistence.migration.SqliteToPostgresMigrationCli \\");
        System.out.println("    --source <sqlite_file_or_jdbc_url> \\");
        System.out.println("    --target <postgres_jdbc_url> \\");
        System.out.println("    [--user <db_user>] [--password <db_password>] [--dry-run]");
    }
}
