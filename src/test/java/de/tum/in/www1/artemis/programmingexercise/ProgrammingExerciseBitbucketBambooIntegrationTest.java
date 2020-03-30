package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationTest;
import de.tum.in.www1.artemis.connector.bamboo.BambooRequestMockProvider;
import de.tum.in.www1.artemis.connector.bitbucket.BitbucketRequestMockProvider;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.AuthoritiesConstants;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.TeamService;
import de.tum.in.www1.artemis.util.*;
import de.tum.in.www1.artemis.web.rest.ParticipationResource;

public class ProgrammingExerciseBitbucketBambooIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private BambooRequestMockProvider bambooRequestMockProvider;

    @Autowired
    private BitbucketRequestMockProvider bitbucketRequestMockProvider;

    @Autowired
    private TeamService teamService;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ParticipationService participationService;

    private Course course;

    private ProgrammingExercise exercise;

    private final static int numberOfStudents = 2;

    private final static String studentLogin = "student1";

    private final static String teamShortName = "team1";

    public class LocalRepo {

        File localRepoFile;

        File originRepoFile;

        Git localGit;

        Git originGit;

        void configureRepos(String localRepoFileName, String originRepoFileName) throws Exception {

            this.localRepoFile = Files.createTempDirectory(localRepoFileName).toFile();
            this.localGit = Git.init().setDirectory(localRepoFile).call();

            this.originRepoFile = Files.createTempDirectory(originRepoFileName).toFile();
            this.originGit = Git.init().setDirectory(originRepoFile).call();

            StoredConfig config = this.localGit.getRepository().getConfig();
            config.setString("remote", "origin", "url", this.originRepoFile.getAbsolutePath());
            config.save();
        }
    }

    LocalRepo exerciseRepo = new LocalRepo();

    LocalRepo testRepo = new LocalRepo();

    LocalRepo solutionRepo = new LocalRepo();

    LocalRepo studentRepo = new LocalRepo();

    LocalRepo studentTeamRepo = new LocalRepo();

    @BeforeEach
    public void setup() throws Exception {
        database.addUsers(numberOfStudents, 1, 1);
        course = database.addEmptyCourse();
        exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests();

        exerciseRepo.configureRepos("exerciseLocalRepo", "exerciseOriginRepo");
        testRepo.configureRepos("testLocalRepo", "testOriginRepo");
        solutionRepo.configureRepos("solutionLocalRepo", "solutionOriginRepo");
        studentRepo.configureRepos("studentRepo", "studentOriginRepo");
        studentTeamRepo.configureRepos("studentTeamRepo", "studentTeamOriginRepo");

        final var projectKey = exercise.getProjectKey();
        String exerciseRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        String testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        String solutionRepoName = projectKey.toLowerCase() + "-" + RepositoryType.SOLUTION.getName();
        String studentRepoName = projectKey.toLowerCase() + "-" + studentLogin;
        String studentTeamRepoName = projectKey.toLowerCase() + "-" + teamShortName;

        var exerciseRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(exerciseRepo.originRepoFile);
        var testRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(testRepo.originRepoFile);
        var solutionRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(solutionRepo.originRepoFile);
        var studentRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(studentRepo.originRepoFile);
        var studentTeamRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(studentTeamRepo.originRepoFile);

        doReturn(exerciseRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, exerciseRepoName);
        doReturn(testRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, testRepoName);
        doReturn(solutionRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, solutionRepoName);
        doReturn(studentRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, studentRepoName);
        doReturn(studentTeamRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, studentTeamRepoName);

        doReturn(gitService.getRepositoryByLocalPath(exerciseRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(exerciseRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(testRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(testRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(solutionRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(solutionRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(studentRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(studentRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(studentTeamRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(studentTeamRepoTestUrl.getURL(), true);

        doReturn(exerciseRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(exerciseRepoTestUrl.getURL());
        doReturn(testRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(testRepoTestUrl.getURL());
        doReturn(solutionRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(solutionRepoTestUrl.getURL());
        doReturn(studentRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(studentRepoTestUrl.getURL());
        doReturn(studentTeamRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(studentTeamRepoTestUrl.getURL());

        doReturn(exerciseRepoName).when(versionControlService).getRepositorySlugFromUrl(exerciseRepoTestUrl.getURL());
        doReturn(testRepoName).when(versionControlService).getRepositorySlugFromUrl(testRepoTestUrl.getURL());
        doReturn(solutionRepoName).when(versionControlService).getRepositorySlugFromUrl(solutionRepoTestUrl.getURL());
        doReturn(studentRepoName).when(versionControlService).getRepositorySlugFromUrl(studentRepoTestUrl.getURL());
        doReturn(studentTeamRepoName).when(versionControlService).getRepositorySlugFromUrl(studentTeamRepoTestUrl.getURL());

        doReturn(projectKey).when(versionControlService).getProjectKeyFromUrl(any());
    }

    @AfterEach
    public void tearDown() throws IOException {
        database.resetDatabase();
        reset(gitService);
        reset(bambooServer);
        bitbucketRequestMockProvider.reset();
        bambooRequestMockProvider.reset();
        resetLocalRepo(exerciseRepo);
        resetLocalRepo(testRepo);
        resetLocalRepo(solutionRepo);
    }

    private void resetLocalRepo(LocalRepo localRepo) throws IOException {
        if (localRepo.localRepoFile != null && localRepo.localRepoFile.exists()) {
            FileUtils.deleteDirectory(localRepo.localRepoFile);
        }
        if (localRepo.localGit != null) {
            localRepo.localGit.close();
        }

        if (localRepo.originRepoFile != null && localRepo.originRepoFile.exists()) {
            FileUtils.deleteDirectory(localRepo.originRepoFile);
        }
        if (localRepo.originGit != null) {
            localRepo.originGit.close();
        }
    }

    @ParameterizedTest
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_validExercise_created(ExerciseMode mode) throws Exception {
        exercise.setMode(mode);
        mockConnectorRequestsForSetup(exercise);
        final var generatedExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);

        exercise.setId(generatedExercise.getId());
        assertThat(exercise).isEqualTo(generatedExercise);
        assertThat(programmingExerciseRepository.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_validExercise_structureOracle() throws Exception {
        mockConnectorRequestsForSetup(exercise);
        final var generatedExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);
        String response = request.putWithResponseBody(ROOT + GENERATE_TESTS.replace("{exerciseId}", generatedExercise.getId() + ""), generatedExercise, String.class,
                HttpStatus.OK);
        assertThat(response).startsWith("Successfully generated the structure oracle");

        List<RevCommit> testRepoCommits = getAllCommits(testRepo.localGit);
        assertThat(testRepoCommits.size()).isEqualTo(2);

        assertThat(testRepoCommits.get(0).getFullMessage()).isEqualTo("Update the structure oracle file.");
        List<DiffEntry> changes = getChanges(testRepo.localGit.getRepository(), testRepoCommits.get(0));
        assertThat(changes.size()).isEqualTo(1);
        assertThat(changes.get(0).getChangeType()).isEqualTo(DiffEntry.ChangeType.MODIFY);
        assertThat(changes.get(0).getOldPath()).endsWith("test.json");

        // Second time leads to a bad request because the file did not change
        var expectedHeaders = new HashMap<String, String>();
        expectedHeaders.put("X-artemisApp-alert", "Did not update the oracle because there have not been any changes to it.");
        request.putWithResponseBody(ROOT + GENERATE_TESTS.replace("{exerciseId}", generatedExercise.getId() + ""), generatedExercise, String.class, HttpStatus.BAD_REQUEST,
                expectedHeaders);
        assertThat(response).startsWith("Successfully generated the structure oracle");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_noTutors_created() throws Exception {
        course.setTeachingAssistantGroupName(null);
        courseRepository.save(course);
        mockConnectorRequestsForSetup(exercise);

        final var generatedExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);

        exercise.setId(generatedExercise.getId());
        assertThat(exercise).isEqualTo(generatedExercise);
        assertThat(programmingExerciseRepository.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = studentLogin, roles = "USER")
    public void startProgrammingExercise_student_correctInitializationState() throws Exception {
        final var course = exercise.getCourse();
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        User user = userRepo.findOneByLogin(studentLogin).orElseThrow();
        final var verifications = mockConnectorRequestsForStartParticipation(exercise, user.getParticipantIdentifier(), Set.of(user));
        final var path = ParticipationResource.Endpoints.ROOT
                + ParticipationResource.Endpoints.START_PARTICIPATION.replace("{courseId}", "" + course.getId()).replace("{exerciseId}", "" + exercise.getId());
        final var participation = request.postWithResponseBody(path, null, ProgrammingExerciseStudentParticipation.class, HttpStatus.CREATED);

        for (final var verification : verifications) {
            verification.performVerification();
        }

        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
    }

    @Test
    @WithMockUser(username = studentLogin, roles = "USER")
    public void startProgrammingExercise_team_correctInitializationState() throws Exception {
        final var course = exercise.getCourse();
        exercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        // create a team for the user (necessary condition before starting an exercise)
        Set<User> students = Set.of(userRepo.findOneByLogin(studentLogin).get());
        Team team = new Team().name("Team 1").shortName(teamShortName).exercise(exercise).students(students);
        team = teamService.save(exercise, team);

        assertThat(team.getStudents()).as("Student was correctly added to team").hasSize(1);

        final var verifications = mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents());
        final var path = ParticipationResource.Endpoints.ROOT
                + ParticipationResource.Endpoints.START_PARTICIPATION.replace("{courseId}", "" + course.getId()).replace("{exerciseId}", "" + exercise.getId());
        final var participation = request.postWithResponseBody(path, null, ProgrammingExerciseStudentParticipation.class, HttpStatus.CREATED);

        for (final var verification : verifications) {
            verification.performVerification();
        }

        assertThat(participation.getInitializationState()).as("Participation should be initialized").isEqualTo(InitializationState.INITIALIZED);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void repositoryAccessIsAdded_whenStudentIsAddedToTeam() throws Exception {
        exercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        // Create a team with students
        Set<User> students = new HashSet<>(userRepo.findAllInGroup("tumuser"));
        Team team = new Team().name("Team 1").shortName(teamShortName).exercise(exercise).students(students);
        team = teamService.save(exercise, team);

        assertThat(team.getStudents()).as("Students were correctly added to team").hasSize(numberOfStudents);

        // Set up mock requests for start participation
        final var verifications = mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents());

        // Add a new student to the team
        User newStudent = ModelFactory.generateActivatedUsers("new-student", new String[] { "tumuser", "testgroup" }, Set.of(new Authority(AuthoritiesConstants.USER)), 1).get(0);
        newStudent = userRepo.save(newStudent);
        team.addStudents(newStudent);

        // Mock repository write permission give call
        final var repositorySlug = (exercise.getProjectKey() + "-" + team.getParticipantIdentifier()).toLowerCase();
        bitbucketRequestMockProvider.mockGiveWritePermission(exercise, repositorySlug, newStudent.getLogin());

        // Start participation with original team
        participationService.startExercise(exercise, team);

        // Update team with new student after participation has already started
        Team serverTeam = request.putWithResponseBody("/api/exercises/" + exercise.getId() + "/teams/" + team.getId(), team, Team.class, HttpStatus.OK);
        assertThat(serverTeam.getStudents()).as("Team students were updated correctly").hasSize(numberOfStudents + 1); // new student was added

        for (final var verification : verifications) {
            verification.performVerification();
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void repositoryAccessIsRemoved_whenStudentIsRemovedFromTeam() throws Exception {
        exercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);

        // Create a team with students
        Set<User> students = new HashSet<>(userRepo.findAllInGroup("tumuser"));
        Team team = new Team().name("Team 1").shortName(teamShortName).exercise(exercise).students(students);
        team = teamService.save(exercise, team);

        assertThat(team.getStudents()).as("Students were correctly added to team").hasSize(numberOfStudents);

        // Set up mock requests for start participation
        final var verifications = mockConnectorRequestsForStartParticipation(exercise, team.getParticipantIdentifier(), team.getStudents());

        // Remove the first student from the team
        User firstStudent = students.iterator().next();
        team.removeStudents(firstStudent);

        // Mock repository access removal call
        final var repositorySlug = (exercise.getProjectKey() + "-" + team.getParticipantIdentifier()).toLowerCase();
        bitbucketRequestMockProvider.mockRemoveMemberFromRepository(repositorySlug, exercise.getProjectKey(), firstStudent);

        // Start participation with original team
        participationService.startExercise(exercise, team);

        // Update team with removed student
        Team serverTeam = request.putWithResponseBody("/api/exercises/" + exercise.getId() + "/teams/" + team.getId(), team, Team.class, HttpStatus.OK);
        assertThat(serverTeam.getStudents()).as("Team students were updated correctly").hasSize(numberOfStudents - 1); // first student was removed

        for (final var verification : verifications) {
            verification.performVerification();
        }
    }

    private List<Verifiable> mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users) throws Exception {
        final var verifications = new LinkedList<Verifiable>();
        bitbucketRequestMockProvider.mockCopyRepositoryForParticipation(exercise, username);
        bitbucketRequestMockProvider.mockConfigureRepository(exercise, username, users);
        verifications.addAll(bambooRequestMockProvider.mockCopyBuildPlanForParticipation(exercise, username));
        verifications.addAll(bambooRequestMockProvider.mockUpdatePlanRepositoryForParticipation(exercise, username));
        bitbucketRequestMockProvider.mockAddWebHooks(exercise);
        return verifications;
    }

    private void mockConnectorRequestsForSetup(ProgrammingExercise exercise) throws IOException, URISyntaxException, GitAPIException, InterruptedException {
        final var projectKey = exercise.getProjectKey();
        String exerciseRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        String testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        String solutionRepoName = projectKey.toLowerCase() + "-" + RepositoryType.SOLUTION.getName();
        bambooRequestMockProvider.mockCheckIfProjectExists(exercise, false);
        bitbucketRequestMockProvider.mockCheckIfProjectExists(exercise, false);
        bitbucketRequestMockProvider.mockCreateProjectForExercise(exercise);
        bitbucketRequestMockProvider.mockCreateRepository(exercise, exerciseRepoName);
        bitbucketRequestMockProvider.mockCreateRepository(exercise, testRepoName);
        bitbucketRequestMockProvider.mockCreateRepository(exercise, solutionRepoName);
        bitbucketRequestMockProvider.mockAddWebHooks(exercise);
        bambooRequestMockProvider.mockRemoveAllDefaultProjectPermissions(exercise);
        bambooRequestMockProvider.mockGiveProjectPermissions(exercise);

        // TODO: check the actual plan and plan permissions that get passed here
        doReturn(null).when(bambooServer).publish(any());
    }

    public List<RevCommit> getAllCommits(Git gitRepo) throws Exception {
        return StreamSupport.stream(gitRepo.log().call().spliterator(), false).collect(Collectors.toList());
    }

    public List<DiffEntry> getChanges(Repository repository, RevCommit commit) throws Exception {

        try (ObjectReader reader = repository.newObjectReader()) {
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset(reader, commit.getParents()[0].getTree());
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, commit.getTree());

            // finally get the list of changed files
            try (Git git = new Git(repository)) {
                List<DiffEntry> diffs = git.diff().setNewTree(newTreeIter).setOldTree(oldTreeIter).call();
                for (DiffEntry entry : diffs) {
                    System.out.println("Entry: " + entry);
                }
                return diffs;
            }
        }
    }
}
