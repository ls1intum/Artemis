package de.tum.cit.aet.artemis.connectors;

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

import de.tum.cit.aet.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.connector.AeolusRequestMockProvider;
import de.tum.cit.aet.artemis.core.service.connectors.aeolus.AeolusBuildPlanService;
import de.tum.cit.aet.artemis.core.service.connectors.aeolus.AeolusBuildScriptGenerationService;
import de.tum.cit.aet.artemis.core.service.connectors.aeolus.AeolusTemplateService;
import de.tum.cit.aet.artemis.core.service.connectors.aeolus.Windfile;
import de.tum.cit.aet.artemis.core.service.connectors.aeolus.WindfileMetadata;
import de.tum.cit.aet.artemis.programming.domain.AeolusTarget;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

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
        windfile.setMetadata(new WindfileMetadata("test", "test", "test", null, null, null, null, null));
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
        programmingExercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
        programmingExercise.getBuildConfig().setBuildPlanConfiguration(getSerializedWindfile());
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        programmingExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExercise.getBuildConfig().setSequentialTestRuns(true);
        programmingExercise.getBuildConfig().setTestwiseCoverageEnabled(true);
        String script = aeolusBuildScriptGenerationService.getScript(programmingExercise);
        assertThat(script).isNotNull();
        assertThat(script).isEqualTo("imagine a result here");
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.PYTHON);
        programmingExercise.setProjectType(null);
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        programmingExercise.getBuildConfig().setSequentialTestRuns(false);
        programmingExercise.getBuildConfig().setTestwiseCoverageEnabled(false);
        programmingExercise.getBuildConfig().setBuildPlanConfiguration(null);
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
