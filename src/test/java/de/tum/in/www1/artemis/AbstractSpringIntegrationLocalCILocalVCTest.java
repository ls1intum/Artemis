package de.tum.in.www1.artemis;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.participation.AbstractBaseProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.localvcci.LocalVCLocalCITestConfig;
import de.tum.in.www1.artemis.localvcci.LocalVCLocalCITestService;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExerciseGroupRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.util.AbstractArtemisIntegrationTest;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;

// Must start up an actual web server such that the tests can communicate with the ArtemisGitServlet using JGit.
// Otherwise, only MockMvc requests could be used. The port this runs on is defined at server.port (see @TestPropertySource).
// Note: Cannot use WebEnvironment.RANDOM_PORT here because artemis.version-control.url must be set to the correct port in the @TestPropertySource annotation.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
@AutoConfigureEmbeddedDatabase
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "localci", "localvc", "scheduling" })
// Note: the server.port property must correspond to the port used in the artemis.version-control.url property.
@TestPropertySource(properties = { "server.port=49152", "artemis.version-control.url=http://localhost:49152", "artemis.version-control.local-vcs-repo-path=${java.io.tmpdir}",
        "artemis.continuous-integration.thread-pool-size=0", "artemis.continuous-integration.build.images.java.default=dummy-docker-image",
        "artemis.user-management.use-external=false" })
// Contains the mock setup for the DockerClient.
@ContextConfiguration(classes = LocalVCLocalCITestConfig.class)
public abstract class AbstractSpringIntegrationLocalCILocalVCTest extends AbstractArtemisIntegrationTest {

    protected static final String TEST_PREFIX = "localvclocalciintegration";

    protected static final Path allFailTestResultsPath = Paths.get("src", "test", "resources", "test-data", "test-results", "java-gradle", "all-fail");

    protected static final Path partlySuccessfulTestResultsPath = Paths.get("src", "test", "resources", "test-data", "test-results", "java-gradle", "partly-successful");

    protected static final Path allSucceedTestResultsPath = Paths.get("src", "test", "resources", "test-data", "test-results", "java-gradle", "all-succeed");

    @Value("${artemis.version-control.url}")
    protected String localVCSBaseUrl;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    protected Path localVCSBasePath;

    @Autowired
    protected LocalVCLocalCITestConfig localVCLocalCITestConfig;

    @Autowired
    protected LocalVCLocalCITestService localVCLocalCITestService;

    @Autowired
    protected ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    protected ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    protected TeamRepository teamRepository;

    @Autowired
    protected ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    protected ExamRepository examRepository;

    @Autowired
    protected StudentExamRepository studentExamRepository;

    @Autowired
    protected TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    @Autowired
    protected SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    @LocalServerPort
    protected int port;

    protected static final String dummyCommitHash = "1234567890abcdef";

    // The error messages returned by JGit contain these Strings that correspond to the HTTP status codes.
    protected static final String notFound = "not found";

    protected static final String notAuthorized = "not authorized";

    protected static final String internalServerError = "500";

    protected static final String forbidden = "not permitted";

    protected Course course;

    protected ProgrammingExercise programmingExercise;

    protected ProgrammingExerciseStudentParticipation studentParticipation;

    protected ProgrammingExerciseStudentParticipation teachingAssistantParticipation;

    protected ProgrammingExerciseStudentParticipation instructorParticipation;

    protected TemplateProgrammingExerciseParticipation templateParticipation;

    protected SolutionProgrammingExerciseParticipation solutionParticipation;

    protected String student1Login;

    protected User student1;

    protected String student2Login;

    protected String tutor1Login;

    protected String instructor1Login;

    protected String projectKey1;

    protected String assignmentRepositorySlug;

    protected String templateRepositorySlug;

    protected String solutionRepositorySlug;

    @BeforeEach
    void initUsersAndExercise() {
        // The port cannot be injected into the LocalVCLocalCITestService because {local.server.port} is not available when the class is instantiated.
        // Thus, "inject" the port from here.
        localVCLocalCITestService.setPort(port);

        List<User> users = database.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        student1Login = TEST_PREFIX + "student1";
        student1 = users.stream().filter(user -> student1Login.equals(user.getLogin())).findFirst().orElseThrow();
        student2Login = TEST_PREFIX + "student2";
        tutor1Login = TEST_PREFIX + "tutor1";
        instructor1Login = TEST_PREFIX + "instructor1";

        // Set the Authentication object for student1 in the SecurityContextHolder.
        // This is necessary because the "database.addStudentParticipationForProgrammingExercise()" below needs the Authentication object set.
        // In tests, this is done using e.g. @WithMockUser(username="student1", roles="USER"), but this does not work on this @BeforeEach method.
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        Authentication authentication = new UsernamePasswordAuthenticationToken(student1Login, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        course = database.addCourseWithOneProgrammingExercise();
        programmingExercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        projectKey1 = programmingExercise.getProjectKey();
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        programmingExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        programmingExercise.setAllowOfflineIde(true);
        programmingExercise.setTestRepositoryUrl(localVCSBaseUrl + "/git/" + projectKey1 + "/" + projectKey1.toLowerCase() + "-tests.git");
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow(); // programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();
        // Set the correct repository URLs for the template and the solution participation.
        templateRepositorySlug = projectKey1.toLowerCase() + "-exercise";
        templateParticipation = programmingExercise.getTemplateParticipation();
        templateParticipation.setRepositoryUrl(localVCSBaseUrl + "/git/" + projectKey1 + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        solutionParticipation = programmingExercise.getSolutionParticipation();
        solutionParticipation.setRepositoryUrl(localVCSBaseUrl + "/git/" + projectKey1 + "/" + projectKey1.toLowerCase() + "-solution.git");
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);

        assignmentRepositorySlug = (projectKey1 + "-" + student1Login).toLowerCase();

        // Add a participation for student1.
        studentParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, student1Login);
        studentParticipation.setRepositoryUrl(String.format(localVCSBaseUrl + "/git/%s/%s.git", projectKey1, assignmentRepositorySlug));
        studentParticipation.setBranch(defaultBranch);
        programmingExerciseStudentParticipationRepository.save(studentParticipation);

        // Add a participation for tutor1.
        teachingAssistantParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, tutor1Login);
        teachingAssistantParticipation.setRepositoryUrl(String.format(localVCSBaseUrl + "/git/%s/%s.git", projectKey1, (projectKey1 + "-" + tutor1Login).toLowerCase()));
        teachingAssistantParticipation.setBranch(defaultBranch);
        programmingExerciseStudentParticipationRepository.save(teachingAssistantParticipation);

        // Add a participation for instructor1.
        instructorParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, instructor1Login);
        instructorParticipation.setRepositoryUrl(String.format(localVCSBaseUrl + "/git/%s/%s.git", projectKey1, (projectKey1 + "-" + instructor1Login).toLowerCase()));
        instructorParticipation.setBranch(defaultBranch);
        programmingExerciseStudentParticipationRepository.save(instructorParticipation);

        localVCLocalCITestService.addTestCases(programmingExercise);
    }

    /**
     * Note: Mocking requests to the VC and CI server is not necessary for local VC and local CI.
     * The VC system is part of the application context and can thus be called directly.
     * For the CI system, all communication with the DockerClient is mocked (see {@link LocalVCLocalCITestConfig}).
     */

    @Override
    public void mockConnectorRequestsForSetup(ProgrammingExercise exercise, boolean failToCreateCiProject) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockConnectorRequestsForImport(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean recreateBuildPlans, boolean addAuxRepos) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockImportProgrammingExerciseWithFailingEnablePlan(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean planExistsInCi,
            boolean shouldPlanEnableFail) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) {

    }

    @Override
    public void mockConnectorRequestsForResumeParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String repoNameInCI, String repoNameInVcs, List<String> triggeredBy) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockRemoveRepositoryAccess(ProgrammingExercise exercise, Team team, User firstStudent) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockRepositoryWritePermissionsForTeam(Team team, User newStudent, ProgrammingExercise exercise, HttpStatus status) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockRepositoryWritePermissionsForStudent(User student, ProgrammingExercise exercise, HttpStatus status) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockRetrieveArtifacts(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockFetchCommitInfo(String projectKey1, String repositorySlug, String hash) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCopyBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockTriggerFailedBuild(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockGrantReadAccess(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockNotifyPush(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockTriggerParticipationBuild(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockTriggerInstructorBuildAll(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void resetMockProvider() {
        // Not implemented for local VC and local CI
    }

    @Override
    public void verifyMocks() {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockUpdateUserInUserManagement(String oldLogin, User user, String password, Set<String> oldGroups) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockUpdateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockFailUpdateCoursePermissionsInCi(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup,
            boolean failToAddUsers, boolean failToRemoveUsers) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCreateUserInUserManagement(User user, boolean userExistsInCi) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockFailToCreateUserInExternalUserManagement(User user, boolean failInVcs, boolean failInCi, boolean failToGetCiUser) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDeleteUserInUserManagement(User user, boolean userExistsInUserManagement, boolean failInVcs, boolean failInCi) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCreateGroupInUserManagement(String groupName) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDeleteGroupInUserManagement(String groupName) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockAddUserToGroupInUserManagement(User user, String group, boolean failInCi) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockRemoveUserFromGroup(User user, String group, boolean failInCi) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDeleteRepository(String projectKey1, String repositoryName, boolean shouldFail) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDeleteProjectInVcs(String projectKey1, boolean shouldFail) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDeleteBuildPlan(String projectKey1, String planName, boolean shouldFail) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDeleteBuildPlanProject(String projectKey1, boolean shouldFail) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockGetBuildPlan(String projectKey1, String planName, boolean planExistsInCi, boolean planIsActive, boolean planIsBuilding, boolean failToGetBuild) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockHealthInCiService(boolean isRunning, HttpStatus httpStatus) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseParticipation participation, String defaultBranch) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCheckIfProjectExistsInVcs(ProgrammingExercise exercise, boolean existsInVcs) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCheckIfProjectExistsInCi(ProgrammingExercise exercise, boolean existsInCi, boolean shouldFail) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockCheckIfBuildPlanExists(String projectKey1, String templateBuildPlanId, boolean buildPlanExists, boolean shouldFail) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockRepositoryUrlIsValid(VcsRepositoryUrl vcsTemplateRepositoryUrl, String projectKey1, boolean b) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockTriggerBuild(AbstractBaseProgrammingExerciseParticipation solutionParticipation) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockTriggerBuildFailed(AbstractBaseProgrammingExerciseParticipation solutionParticipation) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockSetRepositoryPermissionsToReadOnly(VcsRepositoryUrl repositoryUrl, String projectKey1, Set<User> users) throws Exception {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockConfigureRepository(ProgrammingExercise exercise, String participantIdentifier, Set<User> students, boolean userExists) {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockDefaultBranch(ProgrammingExercise programmingExercise) throws IOException, GitLabApiException {
        // Not implemented for local VC and local CI
    }

    @Override
    public void mockUserExists(String username) throws Exception {
        // Not implemented for local VC and local CI
    }
}
