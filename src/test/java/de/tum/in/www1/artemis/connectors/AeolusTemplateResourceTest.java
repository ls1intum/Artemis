package de.tum.in.www1.artemis.connectors;

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

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.service.connectors.aeolus.ScriptAction;
import de.tum.in.www1.artemis.service.connectors.aeolus.Windfile;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

class AeolusTemplateResourceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "aeolusintegration";

    @Autowired
    protected RequestUtilService request;

    @Autowired
    private UserUtilService userUtilService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
    }

    private static Stream<Arguments> templateProvider() {
        return Stream.of(new Object[][] { { "JAVA/PLAIN_GRADLE", 1 }, { "JAVA/PLAIN_GRADLE?sequentialRuns=true", 2 }, { "JAVA/PLAIN_GRADLE?staticAnalysis=true", 2 },
                { "JAVA/PLAIN_GRADLE?staticAnalysis=true&testCoverage=true", 2 }, { "JAVA/PLAIN_MAVEN", 1 }, { "JAVA/PLAIN_MAVEN?sequentialRuns=true", 2 },
                { "JAVA/PLAIN_MAVEN?staticAnalysis=true", 2 }, { "JAVA/PLAIN_MAVEN?staticAnalysis=true&testCoverage=true", 3 }, { "JAVA/MAVEN_BLACKBOX", 5 },
                { "JAVA/MAVEN_BLACKBOX?staticAnalysis=true", 6 }, { "ASSEMBLER", 4 }, { "C/FACT", 2 }, { "C/GCC", 3 }, { "C/GCC?staticAnalysis=true", 3 }, { "KOTLIN", 1 },
                { "KOTLIN?testCoverage=true", 2 }, { "KOTLIN?sequentialRuns=true", 3 }, { "VHDL", 4 }, { "HASKELL", 1 }, { "HASKELL?sequentialRuns=true", 2 }, { "OCAML", 2 },
                { "SWIFT/PLAIN", 1 }, { "SWIFT/PLAIN?staticAnalysis=true", 2 } }).map(params -> Arguments.of(params[0], params[1]));
    }

    @ParameterizedTest
    @MethodSource("templateProvider")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAeolusTemplateFile(String templateKey, Integer expectedScriptActions) throws Exception {
        String template = request.get("/api/aeolus/templates/" + templateKey, HttpStatus.OK, String.class);
        assertThat(template).isNotEmpty();
        Windfile windfile = Windfile.deserialize(template);
        assertWindfileIsCorrect(windfile, expectedScriptActions);
    }

    @Test()
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

    @Test()
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
        assertThat(windfile.getActions().get(0)).isInstanceOf(ScriptAction.class);
    }

    @Test()
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
        request.get("/api/aeolus/templates/JAVA/PLAIN_GRADLE?staticAnalysis=true&sequentialRuns=true&testCoverage=true", HttpStatus.NOT_FOUND, String.class);
    }

    void assertWindfileIsCorrect(Windfile windfile, long expectedScriptActions) {
        assertThat(windfile.getApi()).isEqualTo("v0.0.1");
        assertThat(windfile.getMetadata().getGitCredentials()).isNull();
        assertThat(windfile.getMetadata().getDocker()).isNotNull();
        assertThat(windfile.getScriptActions().size()).isEqualTo(expectedScriptActions);
    }
}
