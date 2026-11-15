package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;

/**
 * The build installs the dependencies with {@code npm ci}, which fails if the lock file of the test repository has no entry for the workspace of the student repository.
 * The templates are read from the classpath, so that an outdated lock file fails here and not when an instructor creates an exercise.
 * The lock file of the static code analysis templates replaces the regular one when static code analysis is enabled, so both have to contain the workspace.
 */
class JavaScriptTemplateLockFileTest {

    private static final String WORKSPACE_PLACEHOLDER = "${studentParentWorkingDirectoryName}";

    private static final String WORKSPACE_LINK_KEY = "\"node_modules/artemis-exercise\": {";

    private static String read(String path) throws Exception {
        try (var stream = new ClassPathResource(path).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @CsvSource({ "javascript, test", "javascript, staticCodeAnalysis/test", "typescript, test", "typescript, staticCodeAnalysis/test" })
    void lockFileShouldContainTheWorkspaceOfTheStudentRepository(String language, String testTemplateDirectory) throws Exception {
        String lockFile = read("templates/" + language + "/" + testTemplateDirectory + "/package-lock.json");
        String studentPackage = read("templates/" + language + "/exercise/package.json");

        assertThat(studentPackage).contains("\"name\": \"artemis-exercise\"");
        // the workspace itself
        assertThat(lockFile).contains("\"" + WORKSPACE_PLACEHOLDER + "\": {").contains("\"name\": \"artemis-exercise\"");
        // the link that npm creates in node_modules, checked inside its own entry, because other entries have no such property
        int linkEntryStart = lockFile.indexOf(WORKSPACE_LINK_KEY);
        assertThat(linkEntryStart).isNotNegative();
        String linkEntry = lockFile.substring(linkEntryStart, lockFile.indexOf("\n        }", linkEntryStart));
        assertThat(linkEntry).contains("\"resolved\": \"" + WORKSPACE_PLACEHOLDER + "\"").contains("\"link\": true");
    }
}
