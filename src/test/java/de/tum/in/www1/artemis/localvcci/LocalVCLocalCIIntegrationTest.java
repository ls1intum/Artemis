package de.tum.in.www1.artemis.localvcci;

import static de.tum.in.www1.artemis.config.Constants.LOCALCI_RESULTS_DIRECTORY;
import static de.tum.in.www1.artemis.config.Constants.LOCALCI_WORKING_DIRECTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.BuildJob;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPolicy;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.repository.BuildJobRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.ldap.LdapUserDto;
import de.tum.in.www1.artemis.util.LocalRepository;

/**
 * This class contains integration tests for the base repositories (template, solution, tests) and the different types of assignment repositories (student assignment, teaching
 * assistant assignment, instructor assignment).
 */
class LocalVCLocalCIIntegrationTest extends AbstractLocalCILocalVCIntegrationTest {

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private BuildJobRepository buildJobRepository;

    // ---- Repository handles ----
    private LocalRepository templateRepository;

    private LocalRepository testsRepository;

    private LocalRepository solutionRepository;

    private LocalRepository assignmentRepository;

    private String teamShortName;

    private String teamRepositorySlug;

    @BeforeEach
    void initRepositories() throws GitAPIException, IOException, URISyntaxException, InvalidNameException {
        templateRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, templateRepositorySlug);

        testsRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, testsRepositorySlug);

        solutionRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, solutionRepositorySlug);

        assignmentRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, assignmentRepositorySlug);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commit hash for the tests repository.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+",
                Map.of("testCommitHash", DUMMY_COMMIT_HASH), Map.of("testCommitHash", DUMMY_COMMIT_HASH));

        teamShortName = "team1";
        teamRepositorySlug = projectKey1.toLowerCase() + "-" + teamShortName;

        // TODO: mock the authorization properly, potentially in each test differently

        var instructor1 = new LdapUserDto().username(TEST_PREFIX + "instructor1");
        instructor1.setUid(new LdapName("cn=instructor1,ou=test,o=lab"));

        var tutor1 = new LdapUserDto().username(TEST_PREFIX + "tutor1");
        tutor1.setUid(new LdapName("cn=tutor1,ou=test,o=lab"));

        var student1 = new LdapUserDto().username(TEST_PREFIX + "student1");
        student1.setUid(new LdapName("cn=student1,ou=test,o=lab"));

        var fakeUser = new LdapUserDto().username(localVCBaseUsername);
        fakeUser.setUid(new LdapName("cn=" + localVCBaseUsername + ",ou=test,o=lab"));

        doReturn(Optional.of(instructor1)).when(ldapUserService).findByUsername(instructor1.getUsername());
        doReturn(Optional.of(tutor1)).when(ldapUserService).findByUsername(tutor1.getUsername());
        doReturn(Optional.of(student1)).when(ldapUserService).findByUsername(student1.getUsername());
        doReturn(Optional.of(fakeUser)).when(ldapUserService).findByUsername(localVCBaseUsername);

        doReturn(true).when(ldapTemplate).compare(anyString(), anyString(), any());
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
        localVCLocalCITestService.testFetchReturnsError(testsRepository.localGit, student1Login, projectKey1, testsRepositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(testsRepository.localGit, student1Login, projectKey1, testsRepositorySlug, FORBIDDEN);

        // Teaching assistants should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(testsRepository.localGit, tutor1Login, projectKey1, testsRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(testsRepository.localGit, tutor1Login, projectKey1, testsRepositorySlug, FORBIDDEN);

        // Instructors should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(testsRepository.localGit, instructor1Login, projectKey1, testsRepositorySlug);

        String commitHash = localVCLocalCITestService.commitFile(testsRepository.localRepoFile.toPath(), testsRepository.localGit);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the commitHash of the tests repository for both the solution and the template repository.
        // Note: The stub needs to receive the same object twice. Usually, specifying one doReturn() is enough to make the stub return the same object on every subsequent call.
        // However, in this case we have it return an InputStream, which will be consumed after returning it the first time, so we need to create two separate ones.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+",
                Map.of("testCommitHash", commitHash), Map.of("testCommitHash", commitHash));

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        // Mock the results for the solution repository build and for the template repository build that will both be triggered as a result of updating the tests.
        Map<String, String> solutionBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(ALL_SUCCEED_TEST_RESULTS_PATH);
        Map<String, String> templateBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(ALL_FAIL_TEST_RESULTS_PATH);
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY, solutionBuildTestResults,
                templateBuildTestResults);

        localVCLocalCITestService.testPushSuccessful(testsRepository.localGit, instructor1Login, projectKey1, testsRepositorySlug);

        // Solution submissions created as a result from a push to the tests repository should contain the last commit of the tests repository.
        localVCLocalCITestService.testLatestSubmission(solutionParticipation.getId(), commitHash, 13, false);
        localVCLocalCITestService.testLatestSubmission(templateParticipation.getId(), commitHash, 0, false);

        await().until(() -> {
            Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(solutionParticipation.getId());
            return buildJobOptional.isPresent() && buildJobOptional.get().getTriggeredByPushTo().equals(RepositoryType.TESTS);
        });
        await().until(() -> {
            Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(templateParticipation.getId());
            return buildJobOptional.isPresent() && buildJobOptional.get().getTriggeredByPushTo().equals(RepositoryType.TESTS);
        });

        // Assert that the build job for the solution was completed before the build job for the template participation has started
        var solutionBuildJob = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(solutionParticipation.getId()).get();
        var templateBuildJob = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(templateParticipation.getId()).get();
        assertThat(solutionBuildJob.getBuildCompletionDate()).isBefore(templateBuildJob.getBuildStartDate());

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFetchPush_solutionRepository() throws Exception {
        // Students should not be able to fetch and push.
        localVCLocalCITestService.testFetchReturnsError(solutionRepository.localGit, student1Login, projectKey1, solutionRepositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(solutionRepository.localGit, student1Login, projectKey1, solutionRepositorySlug, FORBIDDEN);

        // Teaching assistants should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(solutionRepository.localGit, tutor1Login, projectKey1, solutionRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(solutionRepository.localGit, tutor1Login, projectKey1, solutionRepositorySlug, FORBIDDEN);

        // Instructors should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(solutionRepository.localGit, instructor1Login, projectKey1, solutionRepositorySlug);

        String commitHash = localVCLocalCITestService.commitFile(solutionRepository.localRepoFile.toPath(), solutionRepository.localGit);
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("commitHash", commitHash), Map.of("commitHash", commitHash));
        localVCLocalCITestService.mockTestResults(dockerClient, ALL_SUCCEED_TEST_RESULTS_PATH, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(solutionParticipation);

        localVCLocalCITestService.testPushSuccessful(solutionRepository.localGit, instructor1Login, projectKey1, solutionRepositorySlug);

        localVCLocalCITestService.testLatestSubmission(solutionParticipation.getId(), commitHash, 13, false);

        await().until(() -> {
            Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(solutionParticipation.getId());
            return buildJobOptional.isPresent() && buildJobOptional.get().getRepositoryType().equals(RepositoryType.SOLUTION);
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFetchPush_templateRepository() throws Exception {
        // Students should not be able to fetch and push.
        localVCLocalCITestService.testFetchReturnsError(templateRepository.localGit, student1Login, projectKey1, templateRepositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(templateRepository.localGit, student1Login, projectKey1, templateRepositorySlug, FORBIDDEN);

        // Teaching assistants should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(templateRepository.localGit, tutor1Login, projectKey1, templateRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(templateRepository.localGit, tutor1Login, projectKey1, templateRepositorySlug, FORBIDDEN);

        // Instructors should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(templateRepository.localGit, instructor1Login, projectKey1, templateRepositorySlug);

        String commitHash = localVCLocalCITestService.commitFile(templateRepository.localRepoFile.toPath(), templateRepository.localGit);
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("commitHash", commitHash), Map.of("commitHash", commitHash));
        localVCLocalCITestService.mockTestResults(dockerClient, ALL_FAIL_TEST_RESULTS_PATH, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(templateParticipation);

        localVCLocalCITestService.testPushSuccessful(templateRepository.localGit, instructor1Login, projectKey1, templateRepositorySlug);

        localVCLocalCITestService.testLatestSubmission(templateParticipation.getId(), commitHash, 0, false);

        await().until(() -> {
            Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(templateParticipation.getId());
            return buildJobOptional.isPresent() && buildJobOptional.get().getRepositoryType().equals(RepositoryType.TEMPLATE);
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFetchPush_auxiliaryRepository() throws Exception {

        // setup auxiliary repository
        auxiliaryRepositorySlug = localVCLocalCITestService.getRepositorySlug(projectKey1, "auxiliary");
        List<AuxiliaryRepository> auxiliaryRepositories = auxiliaryRepositoryRepository.findAll();
        AuxiliaryRepository auxRepo = new AuxiliaryRepository();
        auxRepo.setName("auxiliary");
        auxRepo.setCheckoutDirectory("aux");
        auxRepo.setRepositoryUri(localVCBaseUrl + "/git/" + projectKey1 + "/" + auxiliaryRepositorySlug + ".git");
        auxiliaryRepositoryRepository.save(auxRepo);
        auxRepo.setExercise(programmingExercise);
        auxiliaryRepositories.add(auxRepo);

        programmingExercise.setAuxiliaryRepositories(auxiliaryRepositories);
        programmingExerciseRepository.save(programmingExercise);

        LocalRepository auxiliaryRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, auxiliaryRepositorySlug);

        // Students should not be able to fetch and push.
        localVCLocalCITestService.testFetchReturnsError(auxiliaryRepository.localGit, student1Login, projectKey1, auxiliaryRepositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(auxiliaryRepository.localGit, student1Login, projectKey1, auxiliaryRepositorySlug, FORBIDDEN);

        // Teaching assistants should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(auxiliaryRepository.localGit, tutor1Login, projectKey1, auxiliaryRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(auxiliaryRepository.localGit, tutor1Login, projectKey1, auxiliaryRepositorySlug, FORBIDDEN);

        // Instructors should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(auxiliaryRepository.localGit, instructor1Login, projectKey1, auxiliaryRepositorySlug);

        String commitHash = localVCLocalCITestService.commitFile(auxiliaryRepository.localRepoFile.toPath(), auxiliaryRepository.localGit);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the commitHash of the tests repository for both the solution and the template repository.
        // Note: The stub needs to receive the same object twice. Usually, specifying one doReturn() is enough to make the stub return the same object on every subsequent call.
        // However, in this case we have it return an InputStream, which will be consumed after returning it the first time, so we need to create two separate ones.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+",
                Map.of("testCommitHash", commitHash), Map.of("testCommitHash", commitHash));

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        // Mock the results for the solution repository build and for the template repository build that will both be triggered as a result of updating the tests.
        Map<String, String> solutionBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(ALL_SUCCEED_TEST_RESULTS_PATH);
        Map<String, String> templateBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(ALL_FAIL_TEST_RESULTS_PATH);
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY, solutionBuildTestResults,
                templateBuildTestResults);

        localVCLocalCITestService.testPushSuccessful(auxiliaryRepository.localGit, instructor1Login, projectKey1, auxiliaryRepositorySlug);

        // Solution submissions created as a result from a push to the auxiliary repository should contain the last commit of the tests repository.
        localVCLocalCITestService.testLatestSubmission(solutionParticipation.getId(), commitHash, 13, false);
        localVCLocalCITestService.testLatestSubmission(templateParticipation.getId(), commitHash, 0, false);

        await().until(() -> {
            Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(templateParticipation.getId());
            return buildJobOptional.isPresent() && buildJobOptional.get().getRepositoryType().equals(RepositoryType.TEMPLATE);
        });
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
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("commitHash", commitHash), Map.of("commitHash", commitHash));
        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        localVCLocalCITestService.mockTestResults(dockerClient, PARTLY_SUCCESSFUL_TEST_RESULTS_PATH, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 1, false);

        await().until(() -> {
            Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(studentParticipation.getId());
            return buildJobOptional.isPresent() && buildJobOptional.get().getRepositoryType().equals(RepositoryType.USER);
        });

        // Teaching assistant
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);

        // Instructor
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);

        // Student2 should not be able to access the repository of student1.
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.localGit, student2Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, student2Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);

        // Before the start date of the exercise, students aren't able to access their repositories. Usually, the exercise will be configured with a start date in the future and
        // students will not be able to create a repository before that.
        // Teaching assistants should be able to fetch and instructors should be able to fetch and push.
        programmingExercise.setStartDate(ZonedDateTime.now().plusHours(1));
        request.putWithResponseBody("/api/programming-exercises", programmingExercise, ProgrammingExercise.class, HttpStatus.OK);

        // Student
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);

        // Teaching assistant
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);

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
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);

        // Instructor
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testPush_studentAssignmentRepository_tooManySubmissions() throws Exception {
        var participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // LockRepositoryPolicy is enforced
        LockRepositoryPolicy lockRepositoryPolicy = new LockRepositoryPolicy();
        lockRepositoryPolicy.setSubmissionLimit(1);
        lockRepositoryPolicy.setActive(true);

        request.postWithResponseBody("/api/programming-exercises/" + programmingExercise.getId() + "/submission-policy", lockRepositoryPolicy, SubmissionPolicy.class,
                HttpStatus.CREATED);

        // First push should go through.
        String commit = localVCLocalCITestService.commitFile(assignmentRepository.localRepoFile.toPath(), assignmentRepository.localGit);
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("commitHash", commit), Map.of("commitHash", commit));
        localVCLocalCITestService.mockTestResults(dockerClient, PARTLY_SUCCESSFUL_TEST_RESULTS_PATH, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);

        await().until(() -> resultRepository.findFirstByParticipationIdOrderByCompletionDateDesc(participation.getId()).isPresent());

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
        localVCLocalCITestService.testFetchReturnsError(teamLocalRepository.localGit, student2Login, projectKey1, teamRepositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(teamLocalRepository.localGit, student2Login, projectKey1, teamRepositorySlug, FORBIDDEN);

        // Teaching assistant should be able to read but not write.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, tutor1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(teamLocalRepository.localGit, tutor1Login, projectKey1, teamRepositorySlug, FORBIDDEN);

        // Instructor should be able to read and write.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, instructor1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(teamLocalRepository.localGit, instructor1Login, projectKey1, teamRepositorySlug);

        programmingExercise.setStartDate(ZonedDateTime.now().plusMinutes(1));
        request.putWithResponseBody("/api/programming-exercises", programmingExercise, ProgrammingExercise.class, HttpStatus.OK);

        // Student is not able to access repository before start date even if it already exists.
        localVCLocalCITestService.testFetchReturnsError(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug, FORBIDDEN);

        // Teaching assistant should be able to read but not write.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, tutor1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(teamLocalRepository.localGit, tutor1Login, projectKey1, teamRepositorySlug, FORBIDDEN);

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
        localVCLocalCITestService.testPushReturnsError(teamLocalRepository.localGit, tutor1Login, projectKey1, teamRepositorySlug, FORBIDDEN);

        // Instructor should be able to read and write.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, instructor1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(teamLocalRepository.localGit, instructor1Login, projectKey1, teamRepositorySlug);
        teamLocalRepository.resetLocalRepo();
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
        // Teaching assistants should be able to fetch and push to their personal assignment repository before the due date of the exercise.
        // After the due date, they should only be able to fetch.
        // They can currently only push during the working time of the exercise (see https://github.com/ls1intum/Artemis/issues/6422).

        // Create teaching assistant repository.
        String repositorySlug = projectKey1.toLowerCase() + "-" + tutor1Login;
        LocalRepository taAssignmentRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, repositorySlug);

        programmingExercise.setStartDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        // Student
        localVCLocalCITestService.testFetchReturnsError(taAssignmentRepository.localGit, student1Login, projectKey1, repositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(taAssignmentRepository.localGit, student1Login, projectKey1, repositorySlug, FORBIDDEN);

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
        localVCLocalCITestService.testPushReturnsError(taAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug, FORBIDDEN);

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

        localVCLocalCITestService.testFetchReturnsError(instructorAssignmentRepository.localGit, student1Login, projectKey1, repositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(instructorAssignmentRepository.localGit, student1Login, projectKey1, repositorySlug, FORBIDDEN);

        localVCLocalCITestService.testFetchSuccessful(instructorAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushReturnsError(instructorAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug, FORBIDDEN);

        localVCLocalCITestService.testFetchSuccessful(instructorAssignmentRepository.localGit, instructor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushSuccessful(instructorAssignmentRepository.localGit, instructor1Login, projectKey1, repositorySlug);

        programmingExercise.setStartDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testFetchSuccessful(instructorAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushReturnsError(instructorAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug, FORBIDDEN);

        localVCLocalCITestService.testFetchSuccessful(instructorAssignmentRepository.localGit, instructor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushSuccessful(instructorAssignmentRepository.localGit, instructor1Login, projectKey1, repositorySlug);

        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(1));
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testFetchSuccessful(instructorAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushReturnsError(instructorAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug, FORBIDDEN);

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
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().iterator().next();

        programmingExercise.setExerciseGroup(exerciseGroup);
        programmingExerciseRepository.save(programmingExercise);

        // Start date is in the future.
        var now = ZonedDateTime.now();
        exam.setStartDate(now.plusHours(1));
        exam.setEndDate(now.plusHours(2));
        exam.setWorkingTime(3600);
        examRepository.save(exam);

        // Create StudentExam.

        StudentExam studentExam = examUtilService.addStudentExamWithUser(exam, student1);
        studentExam.setExercises(List.of(programmingExercise));
        studentExam.setWorkingTime(exam.getWorkingTime());
        studentExamRepository.save(studentExam);

        // student1 should not be able to fetch or push yet, even if the repository was already prepared.
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);
        // tutor1 should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);
        // instructor1 should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);

        // Working time
        exam.setStartDate(now.minusMinutes(30));
        examRepository.save(exam);

        // student1 should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        String commitHash = localVCLocalCITestService.commitFile(assignmentRepository.localRepoFile.toPath(), assignmentRepository.localGit);
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("commitHash", commitHash), Map.of("commitHash", commitHash));
        localVCLocalCITestService.mockTestResults(dockerClient, PARTLY_SUCCESSFUL_TEST_RESULTS_PATH, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 1, false);

        await().until(() -> {
            Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(studentParticipation.getId());
            return buildJobOptional.isPresent() && buildJobOptional.get().getRepositoryType().equals(RepositoryType.USER);
        });

        // tutor1 should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);
        // instructor1 should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);

        // Due date is in the past but grace period is still active.
        exam.setEndDate(ZonedDateTime.now().minusMinutes(1));
        exam.setGracePeriod(999);
        examRepository.save(exam);
        studentExam.setExam(exam);
        studentExam.setWorkingTime(0);
        studentExamRepository.save(studentExam);

        // student1 should not be able to fetch or push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);
        // tutor1 should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);
        // instructor1 should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);

        // Grace period is over.
        exam.setGracePeriod(0);
        examRepository.save(exam);
        studentExam.setExam(exam);
        studentExamRepository.save(studentExam);

        // student1 should not be able to fetch or push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);
        // tutor1 should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);
        // instructor1 should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFetchPush_instructorExamTestRun() throws Exception {
        Exam exam = examUtilService.addExamWithExerciseGroup(course, true);
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().iterator().next();

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

        await().until(() -> {
            Optional<ProgrammingExerciseStudentParticipation> participation = programmingExerciseStudentParticipationRepository.findById(instructorTestRunParticipation.getId());
            return participation.isPresent() && participation.get().isTestRun();
        });

        // Start test run
        instructorExam.setStartedAndStartDate(ZonedDateTime.now());
        studentExamRepository.save(instructorExam);

        // Student should not able to fetch or push.
        localVCLocalCITestService.testFetchReturnsError(instructorExamTestRunRepository.localGit, student1Login, projectKey1, repositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(instructorExamTestRunRepository.localGit, student1Login, projectKey1, repositorySlug, FORBIDDEN);

        // Tutor should be able to fetch but not push as it's not his participation.
        localVCLocalCITestService.testFetchSuccessful(instructorExamTestRunRepository.localGit, tutor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushReturnsError(instructorExamTestRunRepository.localGit, tutor1Login, projectKey1, repositorySlug, FORBIDDEN);

        // Instructor should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(instructorExamTestRunRepository.localGit, instructor1Login, projectKey1, repositorySlug);

        String commitHash = localVCLocalCITestService.commitFile(instructorExamTestRunRepository.localRepoFile.toPath(), instructorExamTestRunRepository.localGit);
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("commitHash", commitHash), Map.of("commitHash", commitHash));
        localVCLocalCITestService.mockTestResults(dockerClient, PARTLY_SUCCESSFUL_TEST_RESULTS_PATH, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);

        localVCLocalCITestService.testPushSuccessful(instructorExamTestRunRepository.localGit, instructor1Login, projectKey1, repositorySlug);

        localVCLocalCITestService.testLatestSubmission(instructorTestRunParticipation.getId(), commitHash, 1, false);

        await().until(() -> {
            Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(instructorTestRunParticipation.getId());
            return buildJobOptional.isPresent() && buildJobOptional.get().getRepositoryType().equals(RepositoryType.USER);
        });

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
        practiceParticipation.setPracticeMode(true);
        practiceParticipation.setRepositoryUri(localVCLocalCITestService.constructLocalVCUrl("", "", projectKey1, practiceRepositorySlug));
        programmingExerciseStudentParticipationRepository.save(practiceParticipation);

        // Students should be able to fetch and push, teaching assistants should be able to fetch but not push and editors and higher should be able to fetch and push.

        // Student1
        localVCLocalCITestService.testFetchSuccessful(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug);
        String commitHash = localVCLocalCITestService.commitFile(practiceRepository.localRepoFile.toPath(), practiceRepository.localGit);
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("commitHash", commitHash), Map.of("commitHash", commitHash));
        localVCLocalCITestService.mockTestResults(dockerClient, PARTLY_SUCCESSFUL_TEST_RESULTS_PATH, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);
        localVCLocalCITestService.testPushSuccessful(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug);
        localVCLocalCITestService.testLatestSubmission(practiceParticipation.getId(), commitHash, 1, false);

        await().until(() -> {
            Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(practiceParticipation.getId());
            return buildJobOptional.isPresent() && buildJobOptional.get().getRepositoryType().equals(RepositoryType.USER);
        });

        // Teaching assistant
        localVCLocalCITestService.testFetchSuccessful(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug, FORBIDDEN);

        // Instructor
        localVCLocalCITestService.testFetchSuccessful(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug);

        // Student2 should not be able to access the repository of student1.
        localVCLocalCITestService.testFetchReturnsError(practiceRepository.localGit, student2Login, projectKey1, practiceRepositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(practiceRepository.localGit, student2Login, projectKey1, practiceRepositorySlug, FORBIDDEN);

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
        practiceParticipation.setPracticeMode(true);
        programmingExerciseStudentParticipationRepository.save(practiceParticipation);

        // Students should not be able to access, teaching assistants should be able to fetch and push and editors and higher should be able to fetch and push.

        // Student
        localVCLocalCITestService.testFetchReturnsError(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug, FORBIDDEN);

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
        practiceParticipation.setPracticeMode(true);
        programmingExerciseStudentParticipationRepository.save(practiceParticipation);

        // Students should not be able to access, teaching assistants should be able to fetch, and editors and higher should be able to fetch and push.

        // Student
        localVCLocalCITestService.testFetchReturnsError(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug, FORBIDDEN);

        // Teaching assistant
        localVCLocalCITestService.testFetchSuccessful(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug);
        localVCLocalCITestService.testPushReturnsError(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug, FORBIDDEN);

        // Instructor
        localVCLocalCITestService.testFetchSuccessful(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug);

        // Cleanup
        practiceRepository.resetLocalRepo();
    }
}
