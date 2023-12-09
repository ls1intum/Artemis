package de.tum.in.www1.artemis.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.google.gson.JsonParseException;

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

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAeolusTemplateFile() throws Exception {
        Map<String, Integer> templatesWithExpectedScriptActions = new HashMap<>();
        templatesWithExpectedScriptActions.put("JAVA/PLAIN_GRADLE", 1);
        templatesWithExpectedScriptActions.put("JAVA/PLAIN_GRADLE?sequentialRuns=true", 1);
        templatesWithExpectedScriptActions.put("JAVA/PLAIN_MAVEN", 1);
        templatesWithExpectedScriptActions.put("JAVA/PLAIN_MAVEN?sequentialRuns=true", 1);
        templatesWithExpectedScriptActions.put("ASSEMBLER", 4);
        templatesWithExpectedScriptActions.put("C/FACT", 3);
        templatesWithExpectedScriptActions.put("C/GCC", 4);
        templatesWithExpectedScriptActions.put("C/GCC?staticAnalysis=true", 4);
        for (Map.Entry<String, Integer> entry : templatesWithExpectedScriptActions.entrySet()) {
            String template = request.get("/api/aeolus/templates/" + entry.getKey(), HttpStatus.OK, String.class);
            assertThat(template).isNotEmpty();
            Windfile windfile = Windfile.deserialize(template);
            this.assertWindfileIsCorrect(windfile, entry.getValue());
        }
    }

    @Test()
    void testInvalidWindfileDeserialization() {
        try {
            String invalidWindfile = "{\n\"api\": \"v0.0.1\",\n\"metadata\": {\n\"name\": \"example windfile\",\n\"description\": \"example windfile\",\n\"id\": \"example-windfile\"\n},\n\"actions\": [\n{\n\"name\": \"invalid-action\",\n\"runAlways\": true\n}\n]\n}";
            Windfile.deserialize(invalidWindfile);
            fail("Should have thrown an exception as there is no script or platform in the actions object");
        }
        catch (JsonParseException e) {
            assertThat(e.getMessage()).isEqualTo("Cannot determine type");
        }
    }

    @Test()
    void testValidWindfileDeserializationWithClass() {
        String invalidWindfile = "{\n\"api\": \"v0.0.1\",\n\"metadata\": {\n\"name\": \"example windfile\",\n\"description\": \"example windfile\",\n\"id\": \"example-windfile\"\n},\n\"actions\": [\n{\n\"name\": \"valid-action\",\n\"class\": \"script-action\",\n\"script\": \"echo $PATH\",\n\"runAlways\": true\n}\n]\n}";
        Windfile windfile = Windfile.deserialize(invalidWindfile);
        assertThat(windfile).isNotNull();
        assertThat(windfile.getActions().get(0)).isInstanceOf(ScriptAction.class);
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
