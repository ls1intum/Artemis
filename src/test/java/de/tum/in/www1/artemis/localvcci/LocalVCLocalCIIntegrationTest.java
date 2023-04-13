package de.tum.in.www1.artemis.localvcci;

import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.in.www1.artemis.util.LocalRepository;

/**
 * This class contains integration tests for the base repositories (template, solution, tests) and the different types of assignment repositories (student assignment, teaching
 * assistant assignment, instructor assignment).
 */
class LocalVCLocalCIIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    // ---- Repository handles ----
    private String templateRepositorySlug;

    private LocalRepository templateRepository;

    private String testsRepositorySlug;

    private LocalRepository testsRepository;

    private String solutionRepositorySlug;

    private LocalRepository solutionRepository;

    private LocalRepository assignmentRepository;

    @BeforeEach
    void initRepositories() throws GitAPIException, IOException, URISyntaxException {
        templateRepositorySlug = projectKey1.toLowerCase() + "-exercise";
        Path remoteTemplateRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, templateRepositorySlug);
        templateRepository = new LocalRepository(defaultBranch);
        templateRepository.configureRepos("localTemplate", remoteTemplateRepositoryFolder);

        testsRepositorySlug = projectKey1.toLowerCase() + "-tests";
        Path remoteTestsRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, testsRepositorySlug);
        testsRepository = new LocalRepository(defaultBranch);
        testsRepository.configureRepos("localTests", remoteTestsRepositoryFolder);

        solutionRepositorySlug = projectKey1.toLowerCase() + "-solution";
        Path remoteSolutionRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, solutionRepositorySlug);
        solutionRepository = new LocalRepository(defaultBranch);
        solutionRepository.configureRepos("localSolution", remoteSolutionRepositoryFolder);

        Path remoteAssignmentRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, assignmentRepositorySlug);
        assignmentRepository = new LocalRepository(defaultBranch);
        assignmentRepository.configureRepos("localAssignment", remoteAssignmentRepositoryFolder);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commit hash for the tests repository.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(mockDockerClient, "/repositories/test-repository/.git/refs/heads/[^/]+",
                Map.of("testCommitHash", dummyCommitHash), Map.of("testCommitHash", dummyCommitHash));
    }

    @AfterEach
    void removeRepositories() {
        localVCLocalCITestService.removeRepository(templateRepository);
        localVCLocalCITestService.removeRepository(testsRepository);
        localVCLocalCITestService.removeRepository(solutionRepository);
        localVCLocalCITestService.removeRepository(assignmentRepository);
    }

    // ---- Tests for the base repositories ----

    @Test
    void testFetchPush_testsRepository() throws Exception {
        // Students should not be able to fetch and push.
        localVCLocalCITestService.testFetchThrowsException(testsRepository.localGit, student1Login, projectKey1, testsRepositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(testsRepository.localGit, student1Login, projectKey1, testsRepositorySlug, notAuthorized);

        // Teaching assistants should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(testsRepository.localGit, tutor1Login, projectKey1, testsRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(testsRepository.localGit, tutor1Login, projectKey1, testsRepositorySlug, notAuthorized);

        // Instructors should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(testsRepository.localGit, instructor1Login, projectKey1, testsRepositorySlug);

        String commitHash = localVCLocalCITestService.commitFile(testsRepository.localRepoFile.toPath(), programmingExercise.getPackageFolderName(), testsRepository.localGit);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the commitHash of the tests repository for both the solution and the template repository.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(mockDockerClient, "/repositories/assignment-repository/.git/refs/heads/[^/]+",
                Map.of("testCommitHash", commitHash), Map.of("testCommitHash", commitHash));

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        // Mock the results for the solution repository build and for the template repository build that will both be triggered as a result of updating the tests.
        Map<String, String> solutionBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(allSucceedTestResultsPath);
        Map<String, String> templateBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(allFailTestResultsPath);
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(mockDockerClient, "/repositories/test-repository/build/test-results/test", solutionBuildTestResults,
                templateBuildTestResults);

        // Mock GitService.getOrCheckoutRepository().
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testsRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(solutionParticipation);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testsRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(templateParticipation);

        localVCLocalCITestService.testPushSuccessful(testsRepository.localGit, instructor1Login, projectKey1, testsRepositorySlug);

        // Solution submissions created as a result from a push to the tests repository should contain the last commit of the tests repository.
        localVCLocalCITestService.testLastestSubmission(solutionParticipation.getId(), commitHash, 13);
        localVCLocalCITestService.testLastestSubmission(templateParticipation.getId(), commitHash, 0);
    }

    @Test
    void testFetchPush_solutionRepository() throws Exception {
        // Students should not be able to fetch and push.
        localVCLocalCITestService.testFetchThrowsException(solutionRepository.localGit, student1Login, projectKey1, solutionRepositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(solutionRepository.localGit, student1Login, projectKey1, solutionRepositorySlug, notAuthorized);

        // Teaching assistants should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(solutionRepository.localGit, tutor1Login, projectKey1, solutionRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(solutionRepository.localGit, tutor1Login, projectKey1, solutionRepositorySlug, notAuthorized);

        // Instructors should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(solutionRepository.localGit, instructor1Login, projectKey1, solutionRepositorySlug);

        String commitHash = localVCLocalCITestService.commitFile(solutionRepository.localRepoFile.toPath(), programmingExercise.getPackageFolderName(),
                solutionRepository.localGit);
        localVCLocalCITestService.mockCommitHash(mockDockerClient, commitHash);
        localVCLocalCITestService.mockTestResults(mockDockerClient, allSucceedTestResultsPath);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(solutionParticipation);

        localVCLocalCITestService.testPushSuccessful(solutionRepository.localGit, instructor1Login, projectKey1, solutionRepositorySlug);

        localVCLocalCITestService.testLastestSubmission(solutionParticipation.getId(), commitHash, 13);
    }

    @Test
    void testFetchPush_templateRepository() throws Exception {
        // Students should not be able to fetch and push.
        localVCLocalCITestService.testFetchThrowsException(templateRepository.localGit, student1Login, projectKey1, templateRepositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(templateRepository.localGit, student1Login, projectKey1, templateRepositorySlug, notAuthorized);

        // Teaching assistants should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(templateRepository.localGit, tutor1Login, projectKey1, templateRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(templateRepository.localGit, tutor1Login, projectKey1, templateRepositorySlug, notAuthorized);

        // Instructors should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(templateRepository.localGit, instructor1Login, projectKey1, templateRepositorySlug);

        String commitHash = localVCLocalCITestService.commitFile(templateRepository.localRepoFile.toPath(), programmingExercise.getPackageFolderName(),
                templateRepository.localGit);
        localVCLocalCITestService.mockCommitHash(mockDockerClient, commitHash);
        localVCLocalCITestService.mockTestResults(mockDockerClient, allFailTestResultsPath);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(templateParticipation);

        localVCLocalCITestService.testPushSuccessful(templateRepository.localGit, instructor1Login, projectKey1, templateRepositorySlug);

        localVCLocalCITestService.testLastestSubmission(templateParticipation.getId(), commitHash, 0);
    }

    // ---- Tests for the student assignment repository ----

    @Test
    void testFetchPush_studentAssignmentRepository() throws Exception {
        // During working time, students should be able to fetch and push, teaching assistants should be able fetch but not push and editors and higher should be able to fetch and
        // push.

        // Student1
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        String commitHash = localVCLocalCITestService.commitFile(assignmentRepository.localRepoFile.toPath(), programmingExercise.getPackageFolderName(),
                assignmentRepository.localGit);
        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the commitHash for the assignment repository.
        localVCLocalCITestService.mockCommitHash(mockDockerClient, commitHash);
        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        localVCLocalCITestService.mockTestResults(mockDockerClient, partlySuccessfulTestResultsPath);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testLastestSubmission(studentParticipation.getId(), commitHash, 1);

        // Teaching assistant
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, notAuthorized);

        // Instructor
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);

        // Student2 should not be able to access the repository of student1.
        localVCLocalCITestService.testFetchThrowsException(assignmentRepository.localGit, student2Login, projectKey1, assignmentRepositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, student2Login, projectKey1, assignmentRepositorySlug, notAuthorized);

        // Before the start date of the exercise, students are able to fetch if (as in this case) their repository already exists. This is consistent with the behaviour of
        // Bitbucket and GitLab. Usually, the exercise will be configured with a start date in the future and students will not be able to create a repository before that.
        // Teaching assistants should be able to fetch and instructors should be able to fetch and push.
        programmingExercise.setStartDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        // Student
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, forbidden);

        // Teaching assistant
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, notAuthorized);

        // Instructor
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);

        // After the due date of the exercise, students should be able to fetch but not push. Teaching assistants should be able to fetch and instructors should be able to fetch
        // and push.
        programmingExercise.setStartDate(ZonedDateTime.now().minusHours(1));
        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(1));
        programmingExerciseRepository.save(programmingExercise);

        // Student
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, forbidden);

        // Teaching assistant
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, notAuthorized);

        // Instructor
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
    }

    @Test
    void testPush_studentAssignmentRepository_tooManySubmissions() {
        // LockRepositoryPolicy is enforced
        LockRepositoryPolicy lockRepositoryPolicy = new LockRepositoryPolicy();
        lockRepositoryPolicy.setSubmissionLimit(0);
        lockRepositoryPolicy.setActive(true);
        database.addSubmissionPolicyToExercise(lockRepositoryPolicy, programmingExercise);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, forbidden);

        // Instructors should still be able to push
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFetch_studentAssignmentRepository_teamMode() throws GitAPIException, IOException, URISyntaxException {
        programmingExercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(programmingExercise);

        // Create a new team repository.
        String teamShortName = "team1";
        String teamRepositorySlug = projectKey1.toLowerCase() + "-" + teamShortName;
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, teamRepositorySlug);
        LocalRepository teamLocalRepository = new LocalRepository(defaultBranch);
        teamLocalRepository.configureRepos("localTeamRepository", remoteRepositoryFolder);

        // Test without team.
        localVCLocalCITestService.testFetchThrowsException(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug, internalServerError);
        localVCLocalCITestService.testPushThrowsException(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug, internalServerError);

        // Create team.
        Team team = new Team();
        team.setName("Team 1");
        team.setShortName(teamShortName);
        team.setExercise(programmingExercise);
        team.setStudents(new HashSet<>(List.of(student1)));
        team.setOwner(student1);
        teamRepository.save(team);

        // Test without participation.
        localVCLocalCITestService.testFetchThrowsException(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug, internalServerError);
        localVCLocalCITestService.testPushThrowsException(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug, internalServerError);

        // Create participation.
        database.addTeamParticipationForProgrammingExercise(programmingExercise, team);

        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug);

        // Try to access the repository as student2, which is not part of the team
        localVCLocalCITestService.testFetchThrowsException(teamLocalRepository.localGit, student2Login, projectKey1, teamRepositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(teamLocalRepository.localGit, student2Login, projectKey1, teamRepositorySlug, notAuthorized);

        // Teaching assistant should be able to read but not write.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, tutor1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(teamLocalRepository.localGit, tutor1Login, projectKey1, teamRepositorySlug, notAuthorized);

        // Instructor should be able to read and write.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, instructor1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(teamLocalRepository.localGit, instructor1Login, projectKey1, teamRepositorySlug);

        programmingExercise.setStartDate(ZonedDateTime.now().plusMinutes(1));
        programmingExerciseRepository.save(programmingExercise);

        // Student is able to read before the start date if the repository already exists.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug, forbidden);

        // Teaching assistant should be able to read but not write.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, tutor1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(teamLocalRepository.localGit, tutor1Login, projectKey1, teamRepositorySlug, notAuthorized);

        // Instructor should be able to read and write.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, instructor1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(teamLocalRepository.localGit, instructor1Login, projectKey1, teamRepositorySlug);

        programmingExercise.setStartDate(ZonedDateTime.now().minusHours(1));
        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(1));
        programmingExerciseRepository.save(programmingExercise);

        // Student should be able to read but not write.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(teamLocalRepository.localGit, student1Login, projectKey1, teamRepositorySlug, forbidden);

        // Teaching assistant should be able to read but not write.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, tutor1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(teamLocalRepository.localGit, tutor1Login, projectKey1, teamRepositorySlug, notAuthorized);

        // Instructor should be able to read and write.
        localVCLocalCITestService.testFetchSuccessful(teamLocalRepository.localGit, instructor1Login, projectKey1, teamRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(teamLocalRepository.localGit, instructor1Login, projectKey1, teamRepositorySlug);

        // Cleanup
        localVCLocalCITestService.removeRepository(teamLocalRepository);
    }

    // ---- Tests for the teaching assistant assignment repository ----

    @Test
    void testFetchPush_teachingAssistantAssignmentRepository() throws GitAPIException, IOException, URISyntaxException {
        // Students should never be able to fetch and push from the teaching assistant assignment repository.
        // Instructors should alway be able to fetch and push to the teaching assistant assignment repository.
        // Teaching assistants should always be able to fetch and push to their personal assignment repository.
        // They can currently only push during the working time of the exercise on Bitbucket and Bamboo (see https://github.com/ls1intum/Artemis/issues/6422).

        // Create teaching assistant repository.
        String repositorySlug = projectKey1.toLowerCase() + "-" + tutor1Login;
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, repositorySlug);
        LocalRepository taAssignmentRepository = new LocalRepository(defaultBranch);
        taAssignmentRepository.configureRepos("localTeachingAssistantAssignment", remoteRepositoryFolder);

        programmingExercise.setStartDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        // Student
        localVCLocalCITestService.testFetchThrowsException(taAssignmentRepository.localGit, student1Login, projectKey1, repositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(taAssignmentRepository.localGit, student1Login, projectKey1, repositorySlug, notAuthorized);

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
        localVCLocalCITestService.removeRepository(taAssignmentRepository);
    }

    // ---- Tests for the instructor assignment repository ----

    @Test
    void testFetchPush_instructorAssignmentRepository() throws GitAPIException, IOException, URISyntaxException {
        // Create instructor assignment repository.
        String repositorySlug = projectKey1.toLowerCase() + "-" + instructor1Login;
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, repositorySlug);
        LocalRepository instructorAssignmentRepository = new LocalRepository(defaultBranch);
        instructorAssignmentRepository.configureRepos("localInstructorAssignment", remoteRepositoryFolder);

        programmingExercise.setStartDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testFetchThrowsException(instructorAssignmentRepository.localGit, student1Login, projectKey1, repositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(instructorAssignmentRepository.localGit, student1Login, projectKey1, repositorySlug, notAuthorized);

        localVCLocalCITestService.testFetchSuccessful(instructorAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushThrowsException(instructorAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug, notAuthorized);

        localVCLocalCITestService.testFetchSuccessful(instructorAssignmentRepository.localGit, instructor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushSuccessful(instructorAssignmentRepository.localGit, instructor1Login, projectKey1, repositorySlug);

        programmingExercise.setStartDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testFetchSuccessful(instructorAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushThrowsException(instructorAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug, notAuthorized);

        localVCLocalCITestService.testFetchSuccessful(instructorAssignmentRepository.localGit, instructor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushSuccessful(instructorAssignmentRepository.localGit, instructor1Login, projectKey1, repositorySlug);

        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(1));
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testFetchSuccessful(instructorAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushThrowsException(instructorAssignmentRepository.localGit, tutor1Login, projectKey1, repositorySlug, notAuthorized);

        localVCLocalCITestService.testFetchSuccessful(instructorAssignmentRepository.localGit, instructor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushSuccessful(instructorAssignmentRepository.localGit, instructor1Login, projectKey1, repositorySlug);

        // Cleanup
        localVCLocalCITestService.removeRepository(instructorAssignmentRepository);
    }

    // ---- Tests for the exam mode ----
    @Test
    void testFetchPush_assignmentRepository_examMode() {
        Exam exam = database.addExamWithExerciseGroup(course, true);
        ExerciseGroup exerciseGroup = exerciseGroupRepository.findByExamId(exam.getId()).stream().findFirst().orElseThrow();

        programmingExercise.setExerciseGroup(exerciseGroup);
        programmingExerciseRepository.save(programmingExercise);

        // Start date is in the future.
        exam.setStartDate(ZonedDateTime.now().plusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(2));
        examRepository.save(exam);

        // Create StudentExam.
        StudentExam studentExam = database.addStudentExam(exam);
        studentExam.setUser(student1);
        studentExam.setExercises(List.of(programmingExercise));
        studentExamRepository.save(studentExam);

        // student1 should not be able to fetch or push yet, even if the repository was already prepared.
        localVCLocalCITestService.testFetchThrowsException(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, forbidden);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, forbidden);
        // tutor1 should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, notAuthorized);
        // instructor1 should be ablet to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);

        // Working time
        exam.setStartDate(ZonedDateTime.now().minusHours(1));
        examRepository.save(exam);
        studentExam.setExam(exam);
        studentExamRepository.save(studentExam);

        // student1 should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        // tutor1 should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, notAuthorized);
        // instructor1 should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);

        // Due date is in the past.
        exam.setEndDate(ZonedDateTime.now().minusMinutes(1));
        examRepository.save(exam);
        studentExam.setExam(exam);
        studentExamRepository.save(studentExam);

        // student1 should not be able to fetch or push.
        localVCLocalCITestService.testFetchThrowsException(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, forbidden);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, forbidden);
        // tutor1 should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, notAuthorized);
        // instructor1 should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
    }

    @Test
    void testFetchPush_instructorExamTestRun() throws GitAPIException, IOException, URISyntaxException {
        Exam exam = database.addExamWithExerciseGroup(course, true);
        ExerciseGroup exerciseGroup = exerciseGroupRepository.findByExamId(exam.getId()).stream().findFirst().orElseThrow();

        programmingExercise.setExerciseGroup(exerciseGroup);
        programmingExerciseRepository.save(programmingExercise);

        // Start date is in the future.
        exam.setStartDate(ZonedDateTime.now().plusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(2));
        examRepository.save(exam);

        // Create an exam test run.
        StudentExam instructorExam = database.addStudentExam(exam);
        instructorExam.setUser(instructor1);
        instructorExam.setExercises(List.of(programmingExercise));
        instructorExam.setTestRun(true);
        studentExamRepository.save(instructorExam);
        ProgrammingExerciseStudentParticipation instructorTestRunParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, instructor1Login);
        instructorTestRunParticipation.setTestRun(true);
        programmingExerciseStudentParticipationRepository.save(instructorTestRunParticipation);

        // Add repository
        String repositorySlug = projectKey1.toLowerCase() + "-" + instructor1Login;
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, repositorySlug);
        LocalRepository instructorExamTestRunRepository = new LocalRepository(defaultBranch);
        instructorExamTestRunRepository.configureRepos("localInstructorExamTestRun", remoteRepositoryFolder);

        // Student should not able to fetch or push.
        localVCLocalCITestService.testFetchThrowsException(instructorExamTestRunRepository.localGit, student1Login, projectKey1, repositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(instructorExamTestRunRepository.localGit, student1Login, projectKey1, repositorySlug, notAuthorized);

        // Tutor should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(instructorExamTestRunRepository.localGit, tutor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushThrowsException(instructorExamTestRunRepository.localGit, tutor1Login, projectKey1, repositorySlug, notAuthorized);

        // Instructor should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(instructorExamTestRunRepository.localGit, instructor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushSuccessful(instructorExamTestRunRepository.localGit, instructor1Login, projectKey1, repositorySlug);

        // Start test run
        instructorExam.setStarted(true);
        studentExamRepository.save(instructorExam);

        // Student should not able to fetch or push.
        localVCLocalCITestService.testFetchThrowsException(instructorExamTestRunRepository.localGit, student1Login, projectKey1, repositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(instructorExamTestRunRepository.localGit, student1Login, projectKey1, repositorySlug, notAuthorized);

        // Tutor should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(instructorExamTestRunRepository.localGit, tutor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushThrowsException(instructorExamTestRunRepository.localGit, tutor1Login, projectKey1, repositorySlug, notAuthorized);

        // Instructor should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(instructorExamTestRunRepository.localGit, instructor1Login, projectKey1, repositorySlug);
        localVCLocalCITestService.testPushSuccessful(instructorExamTestRunRepository.localGit, instructor1Login, projectKey1, repositorySlug);
    }

    // ---- Tests for practice repositories ----

    @Test
    void testFetchPush_studentPracticeRepository() throws Exception {

        // Practice repositories can be created after the due date of an exercise.
        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(1));

        // Create a new practice repository.
        String practiceRepositorySlug = projectKey1.toLowerCase() + "-practice-" + student1Login;
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, practiceRepositorySlug);
        LocalRepository practiceRepository = new LocalRepository(defaultBranch);
        practiceRepository.configureRepos("localPracticeRepository", remoteRepositoryFolder);

        // Test without participation.
        localVCLocalCITestService.testFetchThrowsException(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug, internalServerError);
        localVCLocalCITestService.testPushThrowsException(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug, internalServerError);

        // Create practice participation.
        ProgrammingExerciseStudentParticipation practiceParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, student1Login);
        practiceParticipation.setTestRun(true);
        programmingExerciseStudentParticipationRepository.save(practiceParticipation);

        // Students should be able to fetch and push, teaching assistants should be able fetch but not push and editors and higher should be able to fetch and push.

        // Student1
        localVCLocalCITestService.testFetchSuccessful(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug);

        // Teaching assistant
        localVCLocalCITestService.testFetchSuccessful(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug, notAuthorized);

        // Instructor
        localVCLocalCITestService.testFetchSuccessful(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug);

        // Student2 should not be able to access the repository of student1.
        localVCLocalCITestService.testFetchThrowsException(practiceRepository.localGit, student2Login, projectKey1, practiceRepositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(practiceRepository.localGit, student2Login, projectKey1, practiceRepositorySlug, notAuthorized);

        // Cleanup
        localVCLocalCITestService.removeRepository(practiceRepository);
    }

    @Test
    void testFetchPush_teachingAssistantPracticeRepository() throws Exception {

        // Practice repositories can be created after the due date of an exercise.
        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(1));

        // Create a new practice repository.
        String practiceRepositorySlug = projectKey1.toLowerCase() + "-practice-" + tutor1Login;
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, practiceRepositorySlug);
        LocalRepository practiceRepository = new LocalRepository(defaultBranch);
        practiceRepository.configureRepos("localPracticeRepository", remoteRepositoryFolder);

        // Test without participation.
        localVCLocalCITestService.testFetchThrowsException(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug, internalServerError);
        localVCLocalCITestService.testPushThrowsException(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug, internalServerError);

        // Create practice participation.
        ProgrammingExerciseStudentParticipation practiceParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, tutor1Login);
        practiceParticipation.setTestRun(true);
        programmingExerciseStudentParticipationRepository.save(practiceParticipation);

        // Students should not be able to access, teaching assistants should be able to fetch and push and editors and higher should be able to fetch and push.

        // Student
        localVCLocalCITestService.testFetchThrowsException(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug, notAuthorized);

        // Teaching assistant
        localVCLocalCITestService.testFetchSuccessful(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug);

        // Instructor
        localVCLocalCITestService.testFetchSuccessful(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug);

        // Cleanup
        localVCLocalCITestService.removeRepository(practiceRepository);
    }

    @Test
    void testFetchPush_instructorPracticeRepository() throws Exception {

        // Practice repositories can be created after the due date of an exercise.
        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(1));

        // Create a new practice repository.
        String practiceRepositorySlug = projectKey1.toLowerCase() + "-practice-" + instructor1Login;
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, practiceRepositorySlug);
        LocalRepository practiceRepository = new LocalRepository(defaultBranch);
        practiceRepository.configureRepos("localPracticeRepository", remoteRepositoryFolder);

        // Test without participation.
        localVCLocalCITestService.testFetchThrowsException(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug, internalServerError);
        localVCLocalCITestService.testPushThrowsException(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug, internalServerError);

        // Create practice participation.
        ProgrammingExerciseStudentParticipation practiceParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, instructor1Login);
        practiceParticipation.setTestRun(true);
        programmingExerciseStudentParticipationRepository.save(practiceParticipation);

        // Students should not be able to access, teaching assistants should be able to fetch, and editors and higher should be able to fetch and push.

        // Student
        localVCLocalCITestService.testFetchThrowsException(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(practiceRepository.localGit, student1Login, projectKey1, practiceRepositorySlug, notAuthorized);

        // Teaching assistant
        localVCLocalCITestService.testFetchSuccessful(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(practiceRepository.localGit, tutor1Login, projectKey1, practiceRepositorySlug, notAuthorized);

        // Instructor
        localVCLocalCITestService.testFetchSuccessful(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(practiceRepository.localGit, instructor1Login, projectKey1, practiceRepositorySlug);

        // Cleanup
        localVCLocalCITestService.removeRepository(practiceRepository);
    }
}
