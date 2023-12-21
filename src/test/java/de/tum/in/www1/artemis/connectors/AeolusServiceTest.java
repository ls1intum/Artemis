package de.tum.in.www1.artemis.connectors;

import static de.tum.in.www1.artemis.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.in.www1.artemis.config.Constants.SOLUTION_REPO_NAME;
import static de.tum.in.www1.artemis.config.Constants.TEST_REPO_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.AeolusRequestMockProvider;
import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.enumeration.AeolusTarget;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.service.connectors.aeolus.*;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;

class AeolusServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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
    void testSuccessfulPublishBuildPlan() {
        Windfile mockWindfile = new Windfile();
        var expectedPlanKey = "PLAN";
        mockWindfile.setId("PROJECT-" + expectedPlanKey);

        aeolusRequestMockProvider.mockSuccessfulPublishBuildPlan(AeolusTarget.BAMBOO, expectedPlanKey);
        String key = aeolusBuildPlanService.publishBuildPlan(mockWindfile, AeolusTarget.BAMBOO);
        assertThat(key).isEqualTo(expectedPlanKey);
    }

    /**
     * Fails in publishing a build plan using Aeolus
     */
    @Test
    void testFailedPublishBuildPlan() {
        Windfile mockWindfile = new Windfile();
        var expectedPlanKey = "PLAN";
        mockWindfile.setId("PROJECT-" + expectedPlanKey);

        aeolusRequestMockProvider.mockFailedPublishBuildPlan(AeolusTarget.BAMBOO);
        String key = aeolusBuildPlanService.publishBuildPlan(mockWindfile, AeolusTarget.BAMBOO);
        assertThat(key).isEqualTo(null);
    }

    @Test
    void testRepositoryMapForJavaWindfileCreation() throws URISyntaxException {
        ProgrammingLanguage language = ProgrammingLanguage.JAVA;
        String branch = "develop";
        VcsRepositoryUrl repositoryUrl = new VcsRepositoryUrl("https://bitbucket.server/scm/PROJECT/REPO.git");
        VcsRepositoryUrl testRepositoryUrl = new VcsRepositoryUrl("https://bitbucket.server/scm/PROJECT/REPO-test.git");
        VcsRepositoryUrl solutionRepositoryUrl = new VcsRepositoryUrl("https://bitbucket.server/scm/PROJECT/REPO-solution.git");
        var auxiliaryRepositories = List.of(new AuxiliaryRepository.AuxRepoNameWithUrl("aux1", new VcsRepositoryUrl("https://bitbucket.server/scm/PROJECT/REPO-aux1.git")),
                new AuxiliaryRepository.AuxRepoNameWithUrl("aux2", new VcsRepositoryUrl("https://bitbucket.server/scm/PROJECT/REPO-aux2.git")));
        var map = aeolusBuildPlanService.createRepositoryMapForWindfile(language, branch, false, repositoryUrl, testRepositoryUrl, solutionRepositoryUrl, auxiliaryRepositories);
        assertThat(map).isNotNull();
        var assignmentDirectory = ContinuousIntegrationService.RepositoryCheckoutPath.ASSIGNMENT.forProgrammingLanguage(language);
        var testDirectory = ContinuousIntegrationService.RepositoryCheckoutPath.TEST.forProgrammingLanguage(language);
        assertThat(map).containsKey(TEST_REPO_NAME);
        assertThat(map).containsKey(ASSIGNMENT_REPO_NAME);
        AeolusRepository testRepo = map.get(TEST_REPO_NAME);
        assertThat(testRepo).isNotNull();
        assertThat(testRepo.getBranch()).isEqualTo(branch);
        assertThat(testRepo.getPath()).isEqualTo(testDirectory);
        assertThat(testRepo.getUrl()).isEqualTo(testRepositoryUrl.toString());
        AeolusRepository assignmentRepo = map.get(ASSIGNMENT_REPO_NAME);
        assertThat(assignmentRepo).isNotNull();
        assertThat(assignmentRepo.getBranch()).isEqualTo(branch);
        assertThat(assignmentRepo.getPath()).isEqualTo(assignmentDirectory);
        assertThat(assignmentRepo.getUrl()).isEqualTo(repositoryUrl.toString());
        assertThat(map).doesNotContainKey(SOLUTION_REPO_NAME);
    }

    @Test
    void testRepositoryMapForHaskellWindfileCreation() throws URISyntaxException {
        ProgrammingLanguage language = ProgrammingLanguage.HASKELL;
        String branch = "develop";
        VcsRepositoryUrl repositoryUrl = new VcsRepositoryUrl("https://bitbucket.server/scm/PROJECT/REPO.git");
        VcsRepositoryUrl testRepositoryUrl = new VcsRepositoryUrl("https://bitbucket.server/scm/PROJECT/REPO-test.git");
        VcsRepositoryUrl solutionRepositoryUrl = new VcsRepositoryUrl("https://bitbucket.server/scm/PROJECT/REPO-solution.git");
        var auxiliaryRepositories = List.of(new AuxiliaryRepository.AuxRepoNameWithUrl("aux1", new VcsRepositoryUrl("https://bitbucket.server/scm/PROJECT/REPO-aux1.git")),
                new AuxiliaryRepository.AuxRepoNameWithUrl("aux2", new VcsRepositoryUrl("https://bitbucket.server/scm/PROJECT/REPO-aux2.git")));
        var map = aeolusBuildPlanService.createRepositoryMapForWindfile(language, branch, true, repositoryUrl, testRepositoryUrl, solutionRepositoryUrl, auxiliaryRepositories);
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
        assertThat(testRepo.getUrl()).isEqualTo(testRepositoryUrl.toString());
        AeolusRepository assignmentRepo = map.get(ASSIGNMENT_REPO_NAME);
        assertThat(assignmentRepo).isNotNull();
        assertThat(assignmentRepo.getBranch()).isEqualTo(branch);
        assertThat(assignmentRepo.getPath()).isEqualTo(assignmentDirectory);
        assertThat(assignmentRepo.getUrl()).isEqualTo(repositoryUrl.toString());
        assertThat(map).containsKey(SOLUTION_REPO_NAME);
        AeolusRepository solutionRepo = map.get(SOLUTION_REPO_NAME);
        assertThat(solutionRepo).isNotNull();
        assertThat(solutionRepo.getBranch()).isEqualTo(branch);
        assertThat(solutionRepo.getPath()).isEqualTo(solutionDirectory);
        assertThat(solutionRepo.getUrl()).isEqualTo(solutionRepositoryUrl.toString());
    }

    @Test
    void testReturnsNullonUrlNull() {
        ReflectionTestUtils.setField(aeolusBuildPlanService, "ciUrl", null);
        assertThat(aeolusBuildPlanService.publishBuildPlan(new Windfile(), AeolusTarget.BAMBOO)).isNull();
    }

    @Test
    void testBuildScriptGeneration() {
        aeolusRequestMockProvider.mockGeneratePreview(AeolusTarget.CLI);
        Windfile windfile = new Windfile();
        windfile.setApi("v0.0.1");
        windfile.setMetadata(new WindfileMetadata());
        windfile.getMetadata().setName("test");
        windfile.getMetadata().setDescription("test");
        windfile.getMetadata().setId("test");
        windfile.setActions(List.of(new ScriptAction()));
        String script = aeolusBuildPlanService.generateBuildScript(windfile, AeolusTarget.CLI);
        assertThat(script).isNotNull();
        assertThat(script).isEqualTo("imagine a result here");
    }
}
