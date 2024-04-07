package de.tum.in.www1.artemis.connectors;

import static de.tum.in.www1.artemis.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.in.www1.artemis.config.Constants.SOLUTION_REPO_NAME;
import static de.tum.in.www1.artemis.config.Constants.TEST_REPO_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

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

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.connector.AeolusRequestMockProvider;
import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.domain.enumeration.AeolusTarget;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusBuildPlanService;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusBuildScriptGenerationService;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusRepository;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusTemplateService;
import de.tum.in.www1.artemis.service.connectors.aeolus.ScriptAction;
import de.tum.in.www1.artemis.service.connectors.aeolus.Windfile;
import de.tum.in.www1.artemis.service.connectors.aeolus.WindfileMetadata;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;

class AeolusServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private AeolusRequestMockProvider aeolusRequestMockProvider;

    @Autowired
    @Qualifier("aeolusRestTemplate")
    RestTemplate restTemplate;

    @Value("${aeolus.url}")
    private URL aeolusUrl;

    @Autowired
    AeolusBuildPlanService aeolusBuildPlanService;

    @Autowired
    AeolusTemplateService aeolusTemplateService;

    @Autowired
    AeolusBuildScriptGenerationService aeolusBuildScriptGenerationService;

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

    /**
     * Publishes a build plan using Aeolus
     */
    @Test
    void testSuccessfulPublishBuildPlan() throws JsonProcessingException {
        Windfile mockWindfile = new Windfile();
        var expectedPlanKey = "PLAN";
        mockWindfile.setId("PROJECT-" + expectedPlanKey);

        aeolusRequestMockProvider.mockSuccessfulPublishBuildPlan(AeolusTarget.JENKINS, expectedPlanKey);
        String key = aeolusBuildPlanService.publishBuildPlan(mockWindfile, AeolusTarget.JENKINS);
        assertThat(key).isEqualTo(expectedPlanKey);
    }

    /**
     * Fails in publishing a build plan using Aeolus
     */
    @Test
    void testFailedPublishBuildPlan() throws JsonProcessingException {
        Windfile mockWindfile = new Windfile();
        var expectedPlanKey = "PLAN";
        mockWindfile.setId("PROJECT-" + expectedPlanKey);

        aeolusRequestMockProvider.mockFailedPublishBuildPlan(AeolusTarget.JENKINS);
        String key = aeolusBuildPlanService.publishBuildPlan(mockWindfile, AeolusTarget.JENKINS);
        assertThat(key).isEqualTo(null);
    }

    @Test
    void testRepositoryMapForJavaWindfileCreation() throws URISyntaxException {
        ProgrammingLanguage language = ProgrammingLanguage.JAVA;
        String branch = "develop";
        VcsRepositoryUri repositoryUri = new VcsRepositoryUri("https://gitlab.server/scm/PROJECT/REPO.git");
        VcsRepositoryUri testRepositoryUri = new VcsRepositoryUri("https://gitlab.server/scm/PROJECT/REPO-test.git");
        VcsRepositoryUri solutionRepositoryUri = new VcsRepositoryUri("https://gitlab.server/scm/PROJECT/REPO-solution.git");
        var auxiliaryRepositories = List.of(new AuxiliaryRepository.AuxRepoNameWithUri("aux1", new VcsRepositoryUri("https://gitlab.server/scm/PROJECT/REPO-aux1.git")),
                new AuxiliaryRepository.AuxRepoNameWithUri("aux2", new VcsRepositoryUri("https://gitlab.server/scm/PROJECT/REPO-aux2.git")));
        var map = aeolusBuildPlanService.createRepositoryMapForWindfile(language, branch, false, repositoryUri, testRepositoryUri, solutionRepositoryUri, auxiliaryRepositories);
        assertThat(map).isNotNull();
        var assignmentDirectory = ContinuousIntegrationService.RepositoryCheckoutPath.ASSIGNMENT.forProgrammingLanguage(language);
        var testDirectory = ContinuousIntegrationService.RepositoryCheckoutPath.TEST.forProgrammingLanguage(language);
        assertThat(map).containsKey(TEST_REPO_NAME);
        assertThat(map).containsKey(ASSIGNMENT_REPO_NAME);
        AeolusRepository testRepo = map.get(TEST_REPO_NAME);
        assertThat(testRepo).isNotNull();
        assertThat(testRepo.getBranch()).isEqualTo(branch);
        assertThat(testRepo.getPath()).isEqualTo(testDirectory);
        assertThat(testRepo.getUrl()).isEqualTo(testRepositoryUri.toString());
        AeolusRepository assignmentRepo = map.get(ASSIGNMENT_REPO_NAME);
        assertThat(assignmentRepo).isNotNull();
        assertThat(assignmentRepo.getBranch()).isEqualTo(branch);
        assertThat(assignmentRepo.getPath()).isEqualTo(assignmentDirectory);
        assertThat(assignmentRepo.getUrl()).isEqualTo(repositoryUri.toString());
        assertThat(map).doesNotContainKey(SOLUTION_REPO_NAME);
    }

    @Test
    void testRepositoryMapForHaskellWindfileCreation() throws URISyntaxException {
        ProgrammingLanguage language = ProgrammingLanguage.HASKELL;
        String branch = "develop";
        VcsRepositoryUri repositoryUri = new VcsRepositoryUri("https://gitlab.server/scm/PROJECT/REPO.git");
        VcsRepositoryUri testRepositoryUri = new VcsRepositoryUri("https://gitlab.server/scm/PROJECT/REPO-test.git");
        VcsRepositoryUri solutionRepositoryUri = new VcsRepositoryUri("https://gitlab.server/scm/PROJECT/REPO-solution.git");
        var auxiliaryRepositories = List.of(new AuxiliaryRepository.AuxRepoNameWithUri("aux1", new VcsRepositoryUri("https://gitlab.server/scm/PROJECT/REPO-aux1.git")),
                new AuxiliaryRepository.AuxRepoNameWithUri("aux2", new VcsRepositoryUri("https://gitlab.server/scm/PROJECT/REPO-aux2.git")));
        var map = aeolusBuildPlanService.createRepositoryMapForWindfile(language, branch, true, repositoryUri, testRepositoryUri, solutionRepositoryUri, auxiliaryRepositories);
        assertThat(map).isNotNull();
        var assignmentDirectory = ContinuousIntegrationService.RepositoryCheckoutPath.ASSIGNMENT.forProgrammingLanguage(language);
        var solutionDirectory = ContinuousIntegrationService.RepositoryCheckoutPath.SOLUTION.forProgrammingLanguage(language);
        var testDirectory = ContinuousIntegrationService.RepositoryCheckoutPath.TEST.forProgrammingLanguage(language);
        assertThat(map).containsKey(TEST_REPO_NAME);
        assertThat(map).containsKey(ASSIGNMENT_REPO_NAME);
        AeolusRepository testRepo = map.get(TEST_REPO_NAME);
        assertThat(testRepo).isNotNull();
        assertThat(testRepo.getBranch()).isEqualTo(branch);
        assertThat(testRepo.getPath()).isEqualTo(testDirectory);
        assertThat(testRepo.getUrl()).isEqualTo(testRepositoryUri.toString());
        AeolusRepository assignmentRepo = map.get(ASSIGNMENT_REPO_NAME);
        assertThat(assignmentRepo).isNotNull();
        assertThat(assignmentRepo.getBranch()).isEqualTo(branch);
        assertThat(assignmentRepo.getPath()).isEqualTo(assignmentDirectory);
        assertThat(assignmentRepo.getUrl()).isEqualTo(repositoryUri.toString());
        assertThat(map).containsKey(SOLUTION_REPO_NAME);
        AeolusRepository solutionRepo = map.get(SOLUTION_REPO_NAME);
        assertThat(solutionRepo).isNotNull();
        assertThat(solutionRepo.getBranch()).isEqualTo(branch);
        assertThat(solutionRepo.getPath()).isEqualTo(solutionDirectory);
        assertThat(solutionRepo.getUrl()).isEqualTo(solutionRepositoryUri.toString());
    }

    @Test
    void testReturnsNullonUrlNull() throws JsonProcessingException {
        ReflectionTestUtils.setField(aeolusBuildPlanService, "ciUrl", null);
        assertThat(aeolusBuildPlanService.publishBuildPlan(new Windfile(), AeolusTarget.JENKINS)).isNull();
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
        windfile.setActions(List.of(new ScriptAction()));
        return windfile;
    }

    private String getSerializedWindfile() throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(getWindfile());
    }

    @Test
    void testShouldNotGenerateAnything() throws JsonProcessingException {
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setBuildPlanConfiguration(getSerializedWindfile());
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        programmingExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExercise.setSequentialTestRuns(true);
        programmingExercise.setTestwiseCoverageEnabled(true);
        String script = aeolusBuildScriptGenerationService.getScript(programmingExercise);
        assertThat(script).isNull();
    }

    @Test
    void testGetWindfileFor() throws IOException {
        Windfile windfile = aeolusTemplateService.getWindfileFor(ProgrammingLanguage.JAVA, Optional.empty(), false, false, false);
        assertThat(windfile).isNotNull();
        assertThat(windfile.getActions()).isNotNull();
        assertThat(windfile.getActions()).hasSize(1);
    }

    @Test
    void testGetDefaultWindfileFor() {
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.HASKELL);
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExercise.setSequentialTestRuns(true);
        programmingExercise.setTestwiseCoverageEnabled(true);
        Windfile windfile = aeolusTemplateService.getDefaultWindfileFor(programmingExercise);
        assertThat(windfile).isNull();
    }
}
