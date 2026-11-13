package com.writingassistant.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ServiceArchitectureTest {

    @Test
    void testServiceImplementsAllArchitecturalInterfaces(@TempDir Path tempDir) throws Exception {
        try (ServerDatabase database = new ServerDatabase(tempDir.resolve("arch_test_data"))) {
            Service service = new Service(database);

            // Assert that Service strictly implements all 5 decoupled interface boundaries
            assertTrue(service instanceof AuthOperations, "Service must implement AuthOperations");
            assertTrue(service instanceof DocumentOperations, "Service must implement DocumentOperations");
            assertTrue(service instanceof AnalysisOperations, "Service must implement AnalysisOperations");
            assertTrue(service instanceof ReviewOperations, "Service must implement ReviewOperations");
            assertTrue(service instanceof SystemOperations, "Service must implement SystemOperations");

            // Verify ready check via SystemOperations interface contract
            SystemOperations systemOps = service;
            assertTrue(systemOps.ready(), "Database connection must report ready status");
        }
    }
}
