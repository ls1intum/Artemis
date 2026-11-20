package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.programming.web.repository.FileSubmission;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class JacksonStreamReadConstraintsTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void jsonParserEnforcesConfiguredStreamReadConstraints() {
        var constraints = jsonMapper.tokenStreamFactory().streamReadConstraints();

        assertThat(constraints.getMaxDocumentLength()).isEqualTo(20L * 1024 * 1024);
        assertThat(constraints.getMaxStringLength()).isEqualTo(10 * 1024 * 1024);
        assertThat(constraints.getMaxNestingDepth()).isEqualTo(500);
    }

    @Test
    void jsonParserDeserializesOnlineEditorFileLargerThanTwoMebibytes() {
        var fileContent = "a".repeat(3 * 1024 * 1024);
        var requestBody = """
                [{"fileName":"src/Main.java","fileContent":"%s"}]
                """.formatted(fileContent);
        var submissionListType = jsonMapper.getTypeFactory().constructCollectionType(List.class, FileSubmission.class);

        List<FileSubmission> submissions = jsonMapper.readValue(requestBody, submissionListType);

        assertThat(submissions).singleElement().satisfies(submission -> {
            assertThat(submission.getFileName()).isEqualTo("src/Main.java");
            assertThat(submission.getFileContent()).isEqualTo(fileContent);
        });
    }
}
