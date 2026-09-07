package com.fleetflow.tracking;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces that {@code com.fleetflow.contracts} types are only ever imported from this
 * service's {@code infrastructure/} package (docs/decisions/0004). Deliberately duplicated
 * per service rather than shared, so it survives {@code mvn -pl <module> test}.
 */
class ContractBoundaryTest {

    private static final Pattern CONTRACTS_IMPORT =
            Pattern.compile("^import\\s+(static\\s+)?com\\.fleetflow\\.contracts.*;");

    @Test
    void contractTypesAreImportedOnlyFromInfrastructure() throws IOException {
        Path mainJava = Path.of("src", "main", "java");
        List<String> violations;
        try (Stream<Path> files = Files.walk(mainJava)) {
            violations = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/infrastructure/"))
                    .filter(ContractBoundaryTest::importsContracts)
                    .map(Path::toString)
                    .toList();
        }
        assertTrue(violations.isEmpty(),
                "com.fleetflow.contracts imported outside infrastructure/: " + violations);
    }

    private static boolean importsContracts(Path file) {
        try {
            return Files.lines(file).anyMatch(line -> CONTRACTS_IMPORT.matcher(line.strip()).find());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
