package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

/**
 * The build installs the dependencies with {@code npm ci}, which fails if the lock file of the test repository has no entry for the workspace of the student repository.
 * The templates are read from the classpath, so that an outdated lock file fails here and not when an instructor creates an exercise.
 */
class JavaScriptTemplateLockFileTest {

    private static final String WORKSPACE_PLACEHOLDER = "${studentParentWorkingDirectoryName}";

    private static String read(String path) throws Exception {
        try (var stream = new ClassPathResource(path).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "javascript", "typescript" })
    void lockFileShouldContainTheWorkspaceOfTheStudentRepository(String language) throws Exception {
        String lockFile = read("templates/" + language + "/test/package-lock.json");
        String studentPackageName = read("templates/" + language + "/exercise/package.json");

        assertThat(studentPackageName).contains("\"name\": \"artemis-exercise\"");
        // the workspace itself and the link that npm creates in node_modules
        assertThat(lockFile).contains("\"" + WORKSPACE_PLACEHOLDER + "\": {").contains("\"name\": \"artemis-exercise\"");
        assertThat(lockFile).contains("\"node_modules/artemis-exercise\": {").contains("\"resolved\": \"" + WORKSPACE_PLACEHOLDER + "\"");
    }
}
