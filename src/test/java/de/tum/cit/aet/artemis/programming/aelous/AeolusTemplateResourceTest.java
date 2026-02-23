package de.tum.cit.aet.artemis.programming.aelous;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.programming.dto.aeolus.ScriptAction;
import de.tum.cit.aet.artemis.programming.dto.aeolus.Windfile;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class AeolusTemplateResourceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "aeolusintegration";

    @Autowired
    private UserUtilService userUtilService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
    }

    private record TestProvider(String templateKey, int expectedScriptActions) {
    }

    private static Stream<Arguments> templateProvider() {
        // @formatter:off
        return Stream.of(
            new TestProvider("JAVA/PLAIN_GRADLE", 1),
            new TestProvider("JAVA/PLAIN_GRADLE?sequentialRuns=true", 2),
            new TestProvider("JAVA/PLAIN_GRADLE?staticAnalysis=true", 2),
            new TestProvider("JAVA/PLAIN_MAVEN", 1),
            new TestProvider("JAVA/PLAIN_MAVEN?sequentialRuns=true", 2),
            new TestProvider("JAVA/PLAIN_MAVEN?staticAnalysis=true", 2),
            new TestProvider("JAVA/MAVEN_BLACKBOX", 7),
            new TestProvider("JAVA/MAVEN_BLACKBOX?staticAnalysis=true", 8),
            new TestProvider("ASSEMBLER", 5),
            new TestProvider("C/FACT", 2),
            new TestProvider("C/GCC", 3),
            new TestProvider("C/GCC?staticAnalysis=true", 3),
            new TestProvider("KOTLIN", 1),
            new TestProvider("KOTLIN?sequentialRuns=true", 3),
            new TestProvider("VHDL", 5),
            new TestProvider("HASKELL", 1),
            new TestProvider("HASKELL?sequentialRuns=true", 3),
            new TestProvider("OCAML", 2),
            new TestProvider("SWIFT/PLAIN", 1),
            new TestProvider("SWIFT/PLAIN?staticAnalysis=true", 2),
            new TestProvider("PYTHON", 1),
            new TestProvider("PYTHON?staticAnalysis=true", 2),
            new TestProvider("PYTHON?sequentialRuns=true", 3),
            new TestProvider("RUST", 2),
            new TestProvider("RUST?staticAnalysis=true", 3),
            new TestProvider("TYPESCRIPT", 3),
            new TestProvider("TYPESCRIPT?staticAnalysis=true", 4),
            new TestProvider("C_SHARP", 2),
            new TestProvider("GO", 2),
            new TestProvider("C_PLUS_PLUS", 2),
            new TestProvider("C_PLUS_PLUS?staticAnalysis=true", 3),
            new TestProvider("JAVASCRIPT", 3),
            new TestProvider("JAVASCRIPT?staticAnalysis=true", 4),
            new TestProvider("RUBY", 3),
            new TestProvider("RUBY?staticAnalysis=true", 4),
            new TestProvider("R", 3),
            new TestProvider("R?staticAnalysis=true", 4),
            new TestProvider("DART", 3),
            new TestProvider("DART?staticAnalysis=true", 5),
            new TestProvider("BASH", 4),
            new TestProvider("MATLAB", 2)
        ).map(provider -> Arguments.of(provider.templateKey(), provider.expectedScriptActions()));
        // @formatter:on
    }

    @ParameterizedTest
    @MethodSource("templateProvider")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAeolusTemplateFile(String templateKey, int expectedScriptActions) throws Exception {
        String template = request.get("/api/programming/aeolus/templates/" + templateKey, HttpStatus.OK, String.class);
        assertThat(template).isNotEmpty();
        Windfile windfile = Windfile.deserialize(template);
        assertWindfileIsCorrect(windfile, expectedScriptActions);
    }

    @Test
    void testInvalidWindfileDeserialization() {
        try {
            String invalidWindfile = """
                    {
                      "api": "v0.0.1",
                      "metadata": {
                        "name": "example windfile",
                        "description": "example windfile",
                        "id": "example-windfile"
                      },
                      "actions": [
                        {
                          "name": "invalid-action",
                          "runAlways": true
                        }
                      ]
                    }""";
            Windfile.deserialize(invalidWindfile);
            fail("Should have thrown an exception as there is no script or platform in the actions object");
        }
        catch (JsonProcessingException e) {
            assertThat(e.getMessage()).startsWith("Cannot determine type");
        }
    }

    @Test
    void testValidWindfileDeserializationWithClass() throws JsonProcessingException {
        String validWindfile = """
                {
                  "api": "v0.0.1",
                  "metadata": {
                    "name": "example windfile",
                    "description": "example windfile",
                    "id": "example-windfile"
                  },
                  "actions": [
                    {
                      "name": "valid-action",
                      "class": "script-action",
                      "script": "echo $PATH",
                      "runAlways": true
                    },
                    {
                      "name": "valid-action1",
                      "platform": "jenkins",
                      "runAlways": true
                    },
                    {
                      "name": "valid-action2",
                      "script": "bash script",
                      "runAlways": true
                    }
                  ]
                }""";

        Windfile windfile = Windfile.deserialize(validWindfile);
        assertThat(windfile).isNotNull();
        assertThat(windfile.actions().getFirst()).isInstanceOf(ScriptAction.class);
    }

    @Test
    void testValidWindfileWithInvalidAction() {
        // NOTE: the misspellings are intended
        String invalidWindfile = """
                {
                  "api": "v0.0.1",
                  "metadata": {
                    "name": "example windfile",
                    "description": "example windfile",
                    "id": "example-windfile"
                  },
                  "actions": [
                    {
                      "name": "valid-action",
                      "clsas": "script-action",
                      "scri": "echo $PATH",
                      "runAlways": true
                    }
                  ]
                }""";

        try {
            Windfile.deserialize(invalidWindfile);
            fail("Should have thrown an exception as there is no script or platform in the actions object");
        }
        catch (JsonProcessingException exception) {
            assertThat(exception.getMessage()).startsWith("Cannot determine type");
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetNonExistingAeolusTemplateFile() throws Exception {
        request.get("/api/programming/aeolus/templates/JAVA/PLAIN_GRADLE?staticAnalysis=true&sequentialRuns=true", HttpStatus.NOT_FOUND, String.class);
    }

    void assertWindfileIsCorrect(Windfile windfile, int expectedScriptActions) {
        assertThat(windfile.api()).isEqualTo("v0.0.1");
        assertThat(windfile.metadata().gitCredentials()).isNull();
        assertThat(windfile.metadata().docker()).isNotNull();
        assertThat(windfile.scriptActions().size()).isEqualTo(expectedScriptActions);

        // Every template must capture the compilation exit code and exit with 1 on failure.
        // This ensures that the build agent can detect compilation failures via the container exit code.
        assertThat(windfile.scriptActions()).as("Windfile '%s' must contain compilation exit code detection", windfile.metadata().id())
                .anyMatch(action -> action.script() != null && action.script().contains("COMPILATION_EXIT_CODE=$?") && action.script().contains("exit 1"));
    }
}
