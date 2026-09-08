package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * Server-owned structural-test provenance. Test names drive grading exemptions; repository files are the exact trusted sources allowed to execute with assignment source
 * available.
 */
public record SeededStructuralTests(Set<String> testNames, Map<String, String> repositoryFiles) {

    public static final SeededStructuralTests EMPTY = new SeededStructuralTests(Set.of(), Map.of());

    public SeededStructuralTests {
        testNames = Set.copyOf(testNames);
        repositoryFiles = Map.copyOf(repositoryFiles);
        if (testNames.isEmpty() != repositoryFiles.isEmpty()) {
            throw new IllegalArgumentException("Structural test names and trusted files must either both be empty or both be present");
        }
        if (repositoryFiles.keySet().stream().anyMatch(path -> !isSafeRepositoryPath(path))) {
            throw new IllegalArgumentException("Structural test paths must be safe repository-relative paths");
        }
    }

    private static boolean isSafeRepositoryPath(String path) {
        if (path.isBlank() || path.contains("\\") || path.codePoints().anyMatch(Character::isISOControl)
                || Arrays.stream(path.split("/", -1)).anyMatch(segment -> segment.isBlank() || segment.equals(".") || segment.equals(".."))) {
            return false;
        }
        try {
            Path parsed = Path.of(path);
            return !parsed.isAbsolute() && parsed.normalize().toString().replace('\\', '/').equals(path);
        }
        catch (InvalidPathException exception) {
            return false;
        }
    }
}
