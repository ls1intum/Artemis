package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.LocalRepositoryUriUtil;
import de.tum.cit.aet.artemis.programming.util.MockDelegate;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class ContinuousIntegrationTestService {

    @Value("${artemis.continuous-integration.url}")
    private URI ciServerUrl;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private Path localVCBasePath;

    private final LocalRepository localRepo = new LocalRepository(defaultBranch);

    private ProgrammingExerciseStudentParticipation participation;

    @Autowired
    private GitService gitService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private MockDelegate mockDelegate;

    private ContinuousIntegrationService continuousIntegrationService;

    public ProgrammingExercise programmingExercise;

    /**
     * This method initializes the test case by setting up a local repo
     */
    public void setup(String testPrefix, MockDelegate mockDelegate, ContinuousIntegrationService continuousIntegrationService) throws Exception {
        this.mockDelegate = mockDelegate;
        this.continuousIntegrationService = continuousIntegrationService;

        userUtilService.addUsers(testPrefix, 2, 0, 0, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = (ProgrammingExercise) course.getExercises().iterator().next();

        // init local repo
        String currentLocalFileName = "currentFileName";
        String currentLocalFileContent = "testContent";
        String currentLocalFolderName = "currentFolderName";
        String login = testPrefix + "student1";
        localRepo.configureRepos(localVCBasePath, "testLocalRepo-" + login, "testOriginRepo-" + login);
        // add file to the repository folder
        Path filePath = Path.of(localRepo.workingCopyGitRepoFile + "/" + currentLocalFileName);
        File file = Files.createFile(filePath).toFile();
        // write content to the created file
        FileUtils.write(file, currentLocalFileContent, Charset.defaultCharset());
        // add folder to the repository folder
        filePath = Path.of(localRepo.workingCopyGitRepoFile + "/" + currentLocalFolderName);
        Files.createDirectory(filePath);

        var localRepoUri = new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(localRepo.workingCopyGitRepoFile, localVCBasePath));
        // create a participation

        participation = participationUtilService.addStudentParticipationForProgrammingExerciseForLocalRepo(programmingExercise, login, localRepoUri.getURI());
        assertThat(programmingExercise).as("Exercise was correctly set").isEqualTo(participation.getProgrammingExercise());
    }

    public void tearDown() throws IOException {
        localRepo.resetLocalRepo();
    }

    public ProgrammingExerciseStudentParticipation getParticipation() {
        return participation;
    }

    public void testGetBuildStatusNotFound() throws Exception {
        mockDelegate.mockGetBuildPlan(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId(), false, false, false, false);

        // INACTIVE // same as not found
        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is inactive").isEqualTo(ContinuousIntegrationService.BuildStatus.INACTIVE);
    }

    public void testGetBuildStatusInactive1() throws Exception {
        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        mockDelegate.mockGetBuildPlan(projectKey, buildPlanId, true, false, false, false);

        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is inactive").isEqualTo(ContinuousIntegrationService.BuildStatus.INACTIVE);
    }

    public void testGetBuildStatusInactive2() throws Exception {
        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        mockDelegate.mockGetBuildPlan(projectKey, buildPlanId, true, false, true, false);

        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is inactive").isEqualTo(ContinuousIntegrationService.BuildStatus.INACTIVE);
    }

    public void testGetBuildStatusQueued() throws Exception {
        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        mockDelegate.mockGetBuildPlan(projectKey, buildPlanId, true, true, false, false);

        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is queued").isEqualTo(ContinuousIntegrationService.BuildStatus.QUEUED);
    }

    public void testGetBuildStatusBuilding() throws Exception {
        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        mockDelegate.mockGetBuildPlan(projectKey, buildPlanId, true, true, true, false);

        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is building").isEqualTo(ContinuousIntegrationService.BuildStatus.BUILDING);
    }

    public void testGetBuildStatusFails() throws Exception {
        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        mockDelegate.mockGetBuildPlan(projectKey, buildPlanId, true, true, true, true);

        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).isEqualTo(ContinuousIntegrationService.BuildStatus.INACTIVE);
    }

    public void testHealthRunning() throws Exception {
        mockDelegate.mockHealthInCiService(true, HttpStatus.OK);
        var health = continuousIntegrationService.health();
        assertThat(health.additionalInfo()).containsEntry("url", ciServerUrl);
        assertThat(health.isUp()).isTrue();
    }

    public void testHealthNotRunning() throws Exception {
        mockDelegate.mockHealthInCiService(false, HttpStatus.OK);
        var health = continuousIntegrationService.health();
        assertThat(health.additionalInfo().get("url")).isEqualTo(ciServerUrl);
        assertThat(health.additionalInfo()).containsEntry("url", ciServerUrl);
        assertThat(health.isUp()).isFalse();
    }

    public void testHealthException() throws Exception {
        mockDelegate.mockHealthInCiService(false, HttpStatus.INTERNAL_SERVER_ERROR);
        var health = continuousIntegrationService.health();
        assertThat(health.additionalInfo()).containsEntry("url", ciServerUrl);
        assertThat(health.isUp()).isFalse();
        assertThat(health.exception()).isNotNull();
    }
}
