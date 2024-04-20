package de.tum.in.www1.artemis.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.connector.AeolusRequestMockProvider;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.AeolusTarget;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusBuildPlanService;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusBuildScriptGenerationService;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusTemplateService;
import de.tum.in.www1.artemis.service.connectors.aeolus.Windfile;
import de.tum.in.www1.artemis.service.connectors.aeolus.WindfileMetadata;

class AeolusBuildScriptGenerationServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    @Qualifier("aeolusRestTemplate")
    RestTemplate restTemplate;

    @Autowired
    AeolusBuildPlanService aeolusBuildPlanService;

    @Autowired
    AeolusTemplateService aeolusTemplateService;

    @Autowired
    AeolusBuildScriptGenerationService aeolusBuildScriptGenerationService;

    @Autowired
    private AeolusRequestMockProvider aeolusRequestMockProvider;

    @Value("${aeolus.url}")
    private URL aeolusUrl;

    /**
     * Initializes aeolusRequestMockProvider
     */
    @BeforeEach
    void init() {
        // Create apollonConversionService and inject @Value fields
        aeolusRequestMockProvider = new AeolusRequestMockProvider(restTemplate);
        ReflectionTestUtils.setField(aeolusRequestMockProvider, "aeolusUrl", aeolusUrl);

        aeolusRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        aeolusRequestMockProvider.reset();
    }

    @Test
    void testBuildScriptGeneration() throws JsonProcessingException {
        aeolusRequestMockProvider.mockGeneratePreview(AeolusTarget.CLI);
        String script = aeolusBuildPlanService.generateBuildScript(getWindfile(), AeolusTarget.CLI);
        assertThat(script).isNotNull();
        assertThat(script).isEqualTo("imagine a result here");
    }

    private Windfile getWindfile() {
        Windfile windfile = new Windfile();
        windfile.setApi("v0.0.1");
        windfile.setMetadata(new WindfileMetadata());
        windfile.getMetadata().setName("test");
        windfile.getMetadata().setDescription("test");
        windfile.getMetadata().setId("test");
        return windfile;
    }

    private String getSerializedWindfile() throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(getWindfile());
    }

    @Test
    void testBuildScriptGenerationUsingBuildPlanGenerationService() throws JsonProcessingException {
        aeolusRequestMockProvider.mockGeneratePreview(AeolusTarget.CLI);
        aeolusRequestMockProvider.mockGeneratePreview(AeolusTarget.CLI);
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setBuildPlanConfiguration(getSerializedWindfile());
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        programmingExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExercise.setSequentialTestRuns(true);
        programmingExercise.setTestwiseCoverageEnabled(true);
        String script = aeolusBuildScriptGenerationService.getScript(programmingExercise);
        assertThat(script).isNotNull();
        assertThat(script).isEqualTo("imagine a result here");
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.PYTHON);
        programmingExercise.setProjectType(null);
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        programmingExercise.setSequentialTestRuns(false);
        programmingExercise.setTestwiseCoverageEnabled(false);
        programmingExercise.setBuildPlanConfiguration(null);
        script = aeolusBuildScriptGenerationService.getScript(programmingExercise);
        assertThat(script).isNotNull();
        assertThat(script).isEqualTo("imagine a result here");
    }

    @Test
    void testFailedBuildPlanPublish() throws JsonProcessingException {
        aeolusRequestMockProvider.mockFailedPublishBuildPlan(AeolusTarget.CLI);
        String key = aeolusBuildPlanService.publishBuildPlan(getWindfile(), AeolusTarget.CLI);
        assertThat(key).isNull();
    }

    @Test
    void testNoAuthRestCall() throws JsonProcessingException {
        ReflectionTestUtils.setField(aeolusBuildPlanService, "token", "secret-token");
        aeolusRequestMockProvider.mockAuthenticatedRequest(aeolusUrl + "/publish/" + AeolusTarget.CLI.getName(), "secret-token");
        String key = aeolusBuildPlanService.publishBuildPlan(getWindfile(), AeolusTarget.CLI);
        assertThat(key).isNull();
    }
}
