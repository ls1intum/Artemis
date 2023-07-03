package de.tum.in.www1.artemis.localvcci;

import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPolicy;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;

/**
 * This class contains integration tests for the base repositories (template, solution, tests) and the different types of assignment repositories (student assignment, teaching
 * assistant assignment, instructor assignment).
 */
class LocalVCLocalCIIntegrationTest extends AbstractLocalCILocalVCIntegrationTest {

    @Autowired
    private ExamUtilService examUtilService;

    // ---- Repository handles ----
    private String templateRepositorySlug;

    private LocalRepository templateRepository;

    private String testsRepositorySlug;

    private LocalRepository testsRepository;

    private String solutionRepositorySlug;

    private LocalRepository solutionRepository;

    private LocalRepository assignmentRepository;

    private String teamShortName;

    private String teamRepositorySlug;

    @BeforeEach
    void initRepositories() throws GitAPIException, IOException, URISyntaxException {
        templateRepositorySlug = projectKey1.toLowerCase() + "-exercise";
        templateRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, templateRepositorySlug);

        testsRepositorySlug = projectKey1.toLowerCase() + "-tests";
        testsRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, testsRepositorySlug);

        solutionRepositorySlug = projectKey1.toLowerCase() + "-solution";
        solutionRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, solutionRepositorySlug);

        assignmentRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, assignmentRepositorySlug);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commit hash for the tests repository.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, "/repositories/test-repository/.git/refs/heads/[^/]+",
                Map.of("testCommitHash", DUMMY_COMMIT_HASH), Map.of("testCommitHash", DUMMY_COMMIT_HASH));

        teamShortName = "team1";
        teamRepositorySlug = projectKey1.toLowerCase() + "-" + teamShortName;
    }

    @AfterEach
    void removeRepositories() throws IOException {
        templateRepository.resetLocalRepo();
        testsRepository.resetLocalRepo();
        solutionRepository.resetLocalRepo();
        assignmentRepository.resetLocalRepo();
    }

    // ---- Tests for the base repositories ----

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFetchPush_testsRepository() throws Exception {
        // Students should not be able to fetch and push.
        localVCLocalCITestService.testFetchReturnsError(testsRepository.localGit, student1Login, projectKey1, testsRepositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testPushReturnsError(testsRepository.localGit, student1Login, projectKey1, testsRepositorySlug, NOT_AUTHORIZED);

        // Teaching assistants should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(testsRepository.localGit, tutor1Login, projectKey1, testsRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(testsRepository.localGit, tutor1Login, projectKey1, testsRepositorySlug, NOT_AUTHORIZED);

        // Instructors should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(testsRepository.localGit, instructor1Login, projectKey1, testsRepositorySlug);

        String commitHash = localVCLocalCITestService.commitFile(testsRepository.localRepoFile.toPath(), testsRepository.localGit);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the commitHash of the tests repository for both the solution and the template repository.
        // Note: The stub needs to receive the same object twice. Usually, specifying one doReturn() is enough to make the stub return the same object on every subsequent call.
        // However, in this case we have it return an InputStream, which will be consumed after returning it the first time, so we need to create two separate ones.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, "/repositories/assignment-repository/.git/refs/heads/[^/]+",
                Map.of("testCommitHash", commitHash), Map.of("testCommitHash", commitHash));

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        // Mock the results for the solution repository build and for the template repository build that will both be triggered as a result of updating the tests.
        Map<String, String> solutionBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(ALL_SUCCEED_TEST_RESULTS_PATH);
        Map<String, String> templateBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(ALL_FAIL_TEST_RESULTS_PATH);
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, "/repositories/test-repository/build/test-results/test", solutionBuildTestResults,
                templateBuildTestResults);

        localVCLocalCITestService.testPushSuccessful(testsRepository.localGit, instructor1Login, projectKey1, testsRepositorySlug);

        // Solution submissions created as a result from a push to the tests repository should contain the last commit of the tests repository.
        localVCLocalCITestService.testLatestSubmission(solutionParticipation.getId(), commitHash, 13, false);
        localVCLocalCITestService.testLatestSubmission(templateParticipation.getId(), commitHash, 0, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFetchPush_solutionRepository() throws Exception {
        // Students should not be able to fetch and push.
        localVCLocalCITestService.testFetchReturnsError(solutionRepository.localGit, student1Login, projectKey1, solutionRepositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testPushReturnsError(solutionRepository.localGit, student1Login, projectKey1, solutionRepositorySlug, NOT_AUTHORIZED);

        // Teaching assistants should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(solutionRepository.localGit, tutor1Login, projectKey1, solutionRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(solutionRepository.localGit, tutor1Login, projectKey1, solutionRepositorySlug, NOT_AUTHORIZED);

        // Instructors should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(solutionRepository.localGit, instructor1Login, projectKey1, solutionRepositorySlug);

        String commitHash = localVCLocalCITestService.commitFile(solutionRepository.localRepoFile.toPath(), solutionRepository.localGit);
        localVCLocalCITestService.mockTestResults(dockerClient, ALL_SUCCEED_TEST_RESULTS_PATH);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(solutionParticipation);

        localVCLocalCITestService.testPushSuccessful(solutionRepository.localGit, instructor1Login, projectKey1, solutionRepositorySlug);

        localVCLocalCITestService.testLatestSubmission(solutionParticipation.getId(), commitHash, 13, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFetchPush_templateRepository() throws Exception {
        // Students should not be able to fetch and push.
        localVCLocalCITestService.testFetchReturnsError(templateRepository.localGit, student1Login, projectKey1, templateRepositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testPushReturnsError(templateRepository.localGit, student1Login, projectKey1, templateRepositorySlug, NOT_AUTHORIZED);

        // Teaching assistants should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(templateRepository.localGit, tutor1Login, projectKey1, templateRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(templateRepository.localGit, tutor1Login, projectKey1, templateRepositorySlug, NOT_AUTHORIZED);

        // Instructors should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(templateRepository.localGit, instructor1Login, projectKey1, templateRepositorySlug);

        String commitHash = localVCLocalCITestService.commitFile(templateRepository.localRepoFile.toPath(), templateRepository.localGit);
        localVCLocalCITestService.mockTestResults(dockerClient, ALL_FAIL_TEST_RESULTS_PATH);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(templateParticipation);

        localVCLocalCITestService.testPushSuccessful(templateRepository.localGit, instructor1Login, projectKey1, templateRepositorySlug);

        localVCLocalCITestService.testLatestSubmission(templateParticipation.getId(), commitHash, 0, false);
    }

    // ---- Tests for the student assignment repository ----

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFetchPush_studentAssignmentRepository_beforeAfterStartDate() throws Exception {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // During working time, students should be able to fetch and push, teaching assistants should be able to fetch but not push,
        // and editors and higher should be able to fetch and push.

        // Student1
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        String commitHash = localVCLocalCITestService.commitFile(assignmentRepository.localRepoFile.toPath(), assignmentRepository.localGit);
        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        localVCLocalCITestService.mockTestResults(dockerClient, PARTLY_SUCCESSFUL_TEST_RESULTS_PATH);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 1, false);

        // Teaching assistant
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);

        // Instructor
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);

        // Student2 should not be able to access the repository of student1.
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.localGit, student2Login, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, student2Login, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);

        // Before the start date of the exercise, students are able to fetch if (as in this case) their repository already exists. This is consistent with the behaviour of
        // Bitbucket and GitLab. Usually, the exercise will be configured with a start date in the future and students will not be able to create a repository before that.
        // Teaching assistants should be able to fetch and instructors should be able to fetch and push.
        programmingExercise.setStartDate(ZonedDateTime.now().plusHours(1));
        request.putWithResponseBody("/api/programming-exercises", programmingExercise, ProgrammingExercise.class, HttpStatus.OK);

        // Student
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);

        // Teaching assistant
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);

        // Instructor
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFetchPush_studentAssignmentRepository_afterDueDate() throws Exception {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // After the due date of the exercise, students should be able to fetch but not push.
        // Teaching assistants should be able to fetch and instructors should be able to fetch and push.
        programmingExercise.setStartDate(ZonedDateTime.now().minusHours(1));
        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(1));
        request.putWithResponseBody("/api/programming-exercises", programmingExercise, ProgrammingExercise.class, HttpStatus.OK);

        // Student
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);

        // Teaching assistant
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);

        // Instructor
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testPush_studentAssignmentRepository_tooManySubmissions() throws Exception {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // LockRepositoryPolicy is enforced
        LockRepositoryPolicy lockRepositoryPolicy = new LockRepositoryPolicy();
        lockRepositoryPolicy.setSubmissionLimit(1);
        lockRepositoryPolicy.setActive(true);

        request.postWithResponseBody("/api/programming-exercises/" + programmingExercise.getId() + "/submission-policy", lockRepositoryPolicy, SubmissionPolicy.class,
                HttpStatus.CREATED);

        // First push should go through.
        localVCLocalCITestService.commitFile(assignmentRepository.localRepoFile.toPath(), assignmentRepository.localGit);
        localVCLocalCITestService.mockTestResults(dockerClient, PARTLY_SUCCESSFUL_TEST_RESULTS_PATH);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        // Second push should fail.
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);

        // Instructors should still be able to push.
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
    }

    // ---- Team Mode ----

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFetch_studentAssignmentRepository_teamMode_beforeAfterStartDate() throws Exception {
        LocalRepository teamLocalRepository = prepareTeamExerciseAndRepository();

        // Test without team.
        localVCLocalCITestService.testFetchReturnsError(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug, INTERNAL_SERVER_ERROR);
        localVCLocalCITestService.testPushReturnsError(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug, INTERNAL_SERVER_ERROR);

        // Create team.
        Team team = createTeam();

        // Test without participation.
        localVCLocalCITestService.testFetchReturnsError(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug, INTERNAL_SERVER_ERROR);
        localVCLocalCITestService.testPushReturnsError(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug, INTERNAL_SERVER_ERROR);

        // Create participation.
        participationUtilService.addTeamParticipationForProgrammingExercise(programmingExercise, team);

        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug);

        // Try to access the repository as student2, which is not part of the team
        localVCLocalCITestService.testFetchReturnsError(teamLocalRepository.localGit, student2Login, projectKey1, teamRepositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testPushReturnsError(teamLocalRepository.localGit, student2Login, projectKey1, teamRepositorySlug, NOT_AUTHORIZED);

        // Teaching assistant should be able to read but not write.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, tutor1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(teamLocalRepository.localGit, tutor1Login, projectKey1, teamRepositorySlug, NOT_AUTHORIZED);

        // Instructor should be able to read and write.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, instructor1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(teamLocalRepository.localGit, instructor1Login, projectKey1, teamRepositorySlug);

        programmingExercise.setStartDate(ZonedDateTime.now().plusMinutes(1));
        request.putWithResponseBody("/api/programming-exercises", programmingExercise, ProgrammingExercise.class, HttpStatus.OK);

        // Student is able to read before the start date if the repository already exists.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug, FORBIDDEN);

        // Teaching assistant should be able to read but not write.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, tutor1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(teamLocalRepository.localGit, tutor1Login, projectKey1, teamRepositorySlug, NOT_AUTHORIZED);

        // Instructor should be able to read and write.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, instructor1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(teamLocalRepository.localGit, instructor1Login, projectKey1, teamRepositorySlug);

        // Cleanup
        teamLocalRepository.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFetch_studentAssignmentRepository_teamMode_afterDueDate() throws Exception {
        LocalRepository teamLocalRepository = prepareTeamExerciseAndRepository();
        Team team = createTeam();
        participationUtilService.addTeamParticipationForProgrammingExercise(programmingExercise, team);

        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(1));
        request.putWithResponseBody("/api/programming-exercises", programmingExercise, ProgrammingExercise.class, HttpStatus.OK);

        // Student should be able to read but not write.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug, FORBIDDEN);

        // Teaching assistant should be able to read but not write.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, tutor1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(teamLocalRepository.localGit, tutor1Login, projectKey1, teamRepositorySlug, NOT_AUTHORIZED);

        // Instructor should be able to read and write.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, instructor1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(teamLocalRepository.localGit, instructor1Login, projectKey1, teamRepositorySlug);
    }

    private LocalRepository prepareTeamExerciseAndRepository() throws GitAPIException, IOException, URISyntaxException {
        programmingExercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(programmingExercise);

        // Create a new team repository.
        return localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, teamRepositorySlug);
    }

    private Team createTeam() {
        Team team = new Team();
        team.setName("Team 1");
        team.setShortName(teamShortName);
        team.setExercise(programmingExercise);
        team.setStudents(Set.of(student1));
        team.setOwner(student1);
        return teamRepository.save(team);
    }

    // ---- Tests for the teaching assistant assignment repository ----

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testFetchPush_teachingAssistantAssignmentRepository() throws GitAPIException, IOException, URISyntaxException {
        localVCLocalCITestService.createParticipation(programmingExercise, tutor1Login);

        // Students should never be able to fetch and push from the teaching assistant assignment repository.
        // Instructors should alway be able to fetch and push to the teaching assistant assignment repository.
        // Teaching assistants should always be able to fetch and push to their personal assignment repository.
        // They can currently only push during the working time of the exercise on Bitbucket and Bamboo (see https://github.com/ls1intum/Artemis/issues/6422).

        // Create teaching assistant repository.
        String repositorySlug = projectKey1.toLowerCase() + "-" + tutor1Login;
        LocalRepository taAssignmentRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, repositorySlug);

        programmingExercise.setStartDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        // Student
        localVCLocalCITestService.testFetchReturnsError(taAssignmentRepository.localGit, student1Login, projectKey1, repositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testPushReturnsError(taAssignmentRepository.localGit, student1Login, projectKey1, repositorySlug, NOT_AUTHORIZED);

        // Teaching assistant
        localVCLocalCITestService.testFetchSuccessful(taAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushSuccessful(taAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug);

        // Instructor
        localVCLocalCITestService.testFetchSuccessful(taAssignmentRepository.localGit, instructor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushSuccessful(taAssignmentRepository.localGit, instructor1Login, projectKey1, repositorySlug);

        programmingExercise.setStartDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testFetchSuccessful(taAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushSuccessful(taAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug);

        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(1));
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testFetchSuccessful(taAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushSuccessful(taAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug);

        // Cleanup
        taAssignmentRepository.resetLocalRepo();
    }

    // ---- Tests for the instructor assignment repository ----

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFetchPush_instructorAssignmentRepository() throws GitAPIException, IOException, URISyntaxException {
        localVCLocalCITestService.createParticipation(programmingExercise, instructor1Login);

        // Create instructor assignment repository.
        String repositorySlug = projectKey1.toLowerCase() + "-" + instructor1Login;
        LocalRepository instructorAssignmentRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, repositorySlug);

        programmingExercise.setStartDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testFetchReturnsError(instructorAssignmentRepository.localGit, student1Login, projectKey1, repositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testPushReturnsError(instructorAssignmentRepository.localGit, student1Login, projectKey1, repositorySlug, NOT_AUTHORIZED);

        localVCLocalCITestService.testFetchSuccessful(instructorAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushReturnsError(instructorAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug, NOT_AUTHORIZED);

        localVCLocalCITestService.testFetchSuccessful(instructorAssignmentRepository.localGit, instructor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushSuccessful(instructorAssignmentRepository.localGit, instructor1Login, projectKey1, repositorySlug);

        programmingExercise.setStartDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testFetchSuccessful(instructorAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushReturnsError(instructorAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug, NOT_AUTHORIZED);

        localVCLocalCITestService.testFetchSuccessful(instructorAssignmentRepository.localGit, instructor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushSuccessful(instructorAssignmentRepository.localGit, instructor1Login, projectKey1, repositorySlug);

        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(1));
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testFetchSuccessful(instructorAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushReturnsError(instructorAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug, NOT_AUTHORIZED);

        localVCLocalCITestService.testFetchSuccessful(instructorAssignmentRepository.localGit, instructor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushSuccessful(instructorAssignmentRepository.localGit, instructor1Login, projectKey1, repositorySlug);

        // Cleanup
        instructorAssignmentRepository.resetLocalRepo();
    }

    // ---- Tests for the exam mode ----
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFetchPush_assignmentRepository_examMode() throws Exception {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        Exam exam = examUtilService.addExamWithExerciseGroup(course, true);
        ExerciseGroup exerciseGroup = exerciseGroupRepository.findByExamId(exam.getId()).stream().findFirst().orElseThrow();

        programmingExercise.setExerciseGroup(exerciseGroup);
        programmingExerciseRepository.save(programmingExercise);

        // Start date is in the future.
        exam.setStartDate(ZonedDateTime.now().plusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(2));
        examRepository.save(exam);

        // Create StudentExam.
        StudentExam studentExam = examUtilService.addStudentExam(exam);
        studentExam.setUser(student1);
        studentExam.setExercises(List.of(programmingExercise));
        studentExamRepository.save(studentExam);

        // student1 should not be able to fetch or push yet, even if the repository was already prepared.
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);
        // tutor1 should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
        // instructor1 should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);

        // Working time
        exam.setStartDate(ZonedDateTime.now().minusHours(1));
        examRepository.save(exam);
        studentExam.setExam(exam);
        studentExamRepository.save(studentExam);

        // student1 should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        String commitHash = localVCLocalCITestService.commitFile(assignmentRepository.localRepoFile.toPath(), assignmentRepository.localGit);
        localVCLocalCITestService.mockTestResults(dockerClient, PARTLY_SUCCESSFUL_TEST_RESULTS_PATH);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 1, false);
        // tutor1 should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
        // instructor1 should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);

        // Due date is in the past.
        exam.setEndDate(ZonedDateTime.now().minusMinutes(1));
        examRepository.save(exam);
        studentExam.setExam(exam);
        studentExamRepository.save(studentExam);

        // student1 should not be able to fetch or push.
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);
        // tutor1 should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
        // instructor1 should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFetchPush_instructorExamTestRun() throws Exception {
        Exam exam = examUtilService.addExamWithExerciseGroup(course, true);
        ExerciseGroup exerciseGroup = exerciseGroupRepository.findByExamId(exam.getId()).stream().findFirst().orElseThrow();

        programmingExercise.setExerciseGroup(exerciseGroup);
        programmingExerciseRepository.save(programmingExercise);

        // Start date is in the future.
        exam.setStartDate(ZonedDateTime.now().plusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(2));
        examRepository.save(exam);

        // Create an exam test run.
        StudentExam instructorExam = examUtilService.addStudentExam(exam);
        instructorExam.setUser(instructor1);
        instructorExam.setExercises(List.of(programmingExercise));
        instructorExam.setTestRun(true);
        studentExamRepository.save(instructorExam);

        // Add repository
        String repositorySlug = projectKey1.toLowerCase() + "-" + instructor1Login;
        LocalRepository instructorExamTestRunRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, repositorySlug);

        // First try without participation present.
        localVCLocalCITestService.testFetchReturnsError(instructorExamTestRunRepository.localGit, instructor1Login, projectKey1, repositorySlug, INTERNAL_SERVER_ERROR);

        // Add participation.
        ProgrammingExerciseStudentParticipation instructorTestRunParticipation = localVCLocalCITestService.createParticipation(programmingExercise, instructor1Login);
        instructorTestRunParticipation.setTestRun(true);
        programmingExerciseStudentParticipationRepository.save(instructorTestRunParticipation);

        // Instructor should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(instructorExamTestRunRepository.localGit, instructor1Login, projectKey1, repositorySlug);
        String commitHash = localVCLocalCITestService.commitFile(instructorExamTestRunRepository.localRepoFile.toPath(), instructorExamTestRunRepository.localGit);
        localVCLocalCITestService.mockTestResults(dockerClient, PARTLY_SUCCESSFUL_TEST_RESULTS_PATH);
        localVCLocalCITestService.testPushSuccessful(instructorExamTestRunRepository.localGit, instructor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testLatestSubmission(instructorTestRunParticipation.getId(), commitHash, 1, false);

        // Student should not able to fetch or push.
        localVCLocalCITestService.testFetchReturnsError(instructorExamTestRunRepository.localGit, student1Login, projectKey1, repositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testPushReturnsError(instructorExamTestRunRepository.localGit, student1Login, projectKey1, repositorySlug, NOT_AUTHORIZED);

        // Tutor should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(instructorExamTestRunRepository.localGit, tutor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushReturnsError(instructorExamTestRunRepository.localGit, tutor1Login, projectKey1, repositorySlug, NOT_AUTHORIZED);

        // Start test run
        instructorExam.setStarted(true);
        studentExamRepository.save(instructorExam);

        // Student should not able to fetch or push.
        localVCLocalCITestService.testFetchReturnsError(instructorExamTestRunRepository.localGit, student1Login, projectKey1, repositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testPushReturnsError(instructorExamTestRunRepository.localGit, student1Login, projectKey1, repositorySlug, NOT_AUTHORIZED);

        // Tutor should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(instructorExamTestRunRepository.localGit, tutor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushReturnsError(instructorExamTestRunRepository.localGit, tutor1Login, projectKey1, repositorySlug, NOT_AUTHORIZED);

        // Instructor should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(instructorExamTestRunRepository.localGit, instructor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushSuccessful(instructorExamTestRunRepository.localGit, instructor1Login, projectKey1, repositorySlug);

        // Cleanup
        instructorExamTestRunRepository.resetLocalRepo();
    }

    // ---- Tests for practice repositories ----

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFetchPush_studentPracticeRepository() throws Exception {
        // Practice repositories can be created after the due date of an exercise.
        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(1));

        // Create a new practice repository.
        String practiceRepositorySlug = projectKey1.toLowerCase() + "-practice-" + student1Login;
        LocalRepository practiceRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, practiceRepositorySlug);

        // Test without participation.
        localVCLocalCITestService.testFetchReturnsError(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug, INTERNAL_SERVER_ERROR);
        localVCLocalCITestService.testPushReturnsError(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug, INTERNAL_SERVER_ERROR);

        // Create practice participation.
        ProgrammingExerciseStudentParticipation practiceParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, student1Login);
        practiceParticipation.setTestRun(true);
        practiceParticipation.setRepositoryUrl(localVCLocalCITestService.constructLocalVCUrl("", "", projectKey1, practiceRepositorySlug));
        programmingExerciseStudentParticipationRepository.save(practiceParticipation);

        // Students should be able to fetch and push, teaching assistants should be able to fetch but not push and editors and higher should be able to fetch and push.

        // Student1
        localVCLocalCITestService.testFetchSuccessful(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug);
        String commitHash = localVCLocalCITestService.commitFile(practiceRepository.localRepoFile.toPath(), practiceRepository.localGit);
        localVCLocalCITestService.mockTestResults(dockerClient, PARTLY_SUCCESSFUL_TEST_RESULTS_PATH);
        localVCLocalCITestService.testPushSuccessful(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug);
        localVCLocalCITestService.testLatestSubmission(practiceParticipation.getId(), commitHash, 1, false);

        // Teaching assistant
        localVCLocalCITestService.testFetchSuccessful(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug, NOT_AUTHORIZED);

        // Instructor
        localVCLocalCITestService.testFetchSuccessful(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug);

        // Student2 should not be able to access the repository of student1.
        localVCLocalCITestService.testFetchReturnsError(practiceRepository.localGit, student2Login, projectKey1, practiceRepositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testPushReturnsError(practiceRepository.localGit, student2Login, projectKey1, practiceRepositorySlug, NOT_AUTHORIZED);

        // Cleanup
        practiceRepository.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testFetchPush_teachingAssistantPracticeRepository() throws Exception {

        // Practice repositories can be created after the due date of an exercise.
        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(1));

        // Create a new practice repository.
        String practiceRepositorySlug = projectKey1.toLowerCase() + "-practice-" + tutor1Login;
        LocalRepository practiceRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, practiceRepositorySlug);

        // Test without participation.
        localVCLocalCITestService.testFetchReturnsError(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug, INTERNAL_SERVER_ERROR);
        localVCLocalCITestService.testPushReturnsError(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug, INTERNAL_SERVER_ERROR);

        // Create practice participation.
        ProgrammingExerciseStudentParticipation practiceParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, tutor1Login);
        practiceParticipation.setTestRun(true);
        programmingExerciseStudentParticipationRepository.save(practiceParticipation);

        // Students should not be able to access, teaching assistants should be able to fetch and push and editors and higher should be able to fetch and push.

        // Student
        localVCLocalCITestService.testFetchReturnsError(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testPushReturnsError(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug, NOT_AUTHORIZED);

        // Teaching assistant
        localVCLocalCITestService.testFetchSuccessful(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug);

        // Instructor
        localVCLocalCITestService.testFetchSuccessful(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug);

        // Cleanup
        practiceRepository.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFetchPush_instructorPracticeRepository() throws Exception {

        // Practice repositories can be created after the due date of an exercise.
        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(1));

        // Create a new practice repository.
        String practiceRepositorySlug = projectKey1.toLowerCase() + "-practice-" + instructor1Login;
        LocalRepository practiceRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, practiceRepositorySlug);

        // Test without participation.
        localVCLocalCITestService.testFetchReturnsError(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug, INTERNAL_SERVER_ERROR);
        localVCLocalCITestService.testPushReturnsError(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug, INTERNAL_SERVER_ERROR);

        // Create practice participation.
        ProgrammingExerciseStudentParticipation practiceParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                instructor1Login);
        practiceParticipation.setTestRun(true);
        programmingExerciseStudentParticipationRepository.save(practiceParticipation);

        // Students should not be able to access, teaching assistants should be able to fetch, and editors and higher should be able to fetch and push.

        // Student
        localVCLocalCITestService.testFetchReturnsError(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testPushReturnsError(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug, NOT_AUTHORIZED);

        // Teaching assistant
        localVCLocalCITestService.testFetchSuccessful(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug, NOT_AUTHORIZED);

        // Instructor
        localVCLocalCITestService.testFetchSuccessful(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug);

        // Cleanup
        practiceRepository.resetLocalRepo();
    }
}
