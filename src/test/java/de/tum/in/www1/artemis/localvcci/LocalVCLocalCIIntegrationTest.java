package de.tum.in.www1.artemis.localvcci;

import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import com.github.dockerjava.api.DockerClient;

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
 * This class contains integration tests for the local VC system that should go through successfully to the local CI system.
 */
class LocalVCLocalCIIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private DockerClient mockDockerClient;

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

    // ---- Tests for the assignment repository ----

    /**
     * Test that the connection between the local VC and the local CI system is working.
     * Perform a push to the assignment repository and check that a submission is created and the local CI system successfully builds and tests the source code.
     */
    @Test
    void testFetchPush_assignmentRepository() throws Exception {
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);

        String commitHash = localVCLocalCITestService.commitFile(assignmentRepository.localRepoFile.toPath(), programmingExercise.getPackageFolderName(),
                assignmentRepository.localGit);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the commitHash for the assignment repository.
        localVCLocalCITestService.mockCommitHash(mockDockerClient, commitHash);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        localVCLocalCITestService.mockTestResults(mockDockerClient, partlySuccessfulTestResultsPath);

        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testLastestSubmission(studentParticipation.getId(), commitHash, 1);

        // Student2 tries to access the repository of student1.
        localVCLocalCITestService.testFetchThrowsException(assignmentRepository.localGit, student2Login, projectKey1, assignmentRepositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, student2Login, projectKey1, assignmentRepositorySlug, notAuthorized);

        // Students should not be able to push before the start date.
        programmingExercise.setStartDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(programmingExercise);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, forbidden);

        // Teaching assistants should always be able to fetch student assignment repositories.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, notAuthorized);

        // Instructors should be able to push to the student's assignment repository.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);

        // Students should not be able to push after the due date.
        programmingExercise.setStartDate(ZonedDateTime.now().minusHours(1));
        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(1));
        programmingExerciseRepository.save(programmingExercise);
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, forbidden);

        // Teaching assistants should always be able to fetch student assignment repositories.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug, notAuthorized);
    }

    @Test
    void testPush_assignmentRepository_student_tooManySubmissions() {
        // LockRepositoryPolicy is enforced
        LockRepositoryPolicy lockRepositoryPolicy = new LockRepositoryPolicy();
        lockRepositoryPolicy.setSubmissionLimit(0);
        lockRepositoryPolicy.setActive(true);
        database.addSubmissionPolicyToExercise(lockRepositoryPolicy, programmingExercise);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, forbidden);
    }

    @Test
    void testFetchPush_teachingAssistantAssignmentRepository() throws GitAPIException, IOException, URISyntaxException {
        // Students should never be able to fetch and push from the teaching assistant assignment repository.
        // Instructors should alway be able to fetch and push to the teaching assistant assignment repository.
        // Teaching assistants should always be able to fetch from their personal assignment repository.
        // They can currently only push during the working time of the exercise.
        // Note: Resolving https://github.com/ls1intum/Artemis/issues/6422 will enable teaching assistants to push to their personal assignment repository at any time. This test
        // will have to be adapted then.

        // Create teaching assistant repository.
        String repositorySlug = projectKey1.toLowerCase() + "-" + tutor1Login;
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, repositorySlug);
        LocalRepository repository = new LocalRepository(defaultBranch);
        repository.configureRepos("localTeachingAssistantAssignment", remoteRepositoryFolder);
        Git remoteGit = repository.originGit;
        Path localRepositoryFolder = repository.localRepoFile.toPath();
        Git localGit = repository.localGit;

        programmingExercise.setStartDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        // Student
        localVCLocalCITestService.testFetchThrowsException(localGit, student1Login, projectKey1, repositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(localGit, student1Login, projectKey1, repositorySlug, notAuthorized);

        // Instructor
        localVCLocalCITestService.testFetchSuccessful(localGit, instructor1Login, projectKey1, repositorySlug);

        // Teaching assistant
        localVCLocalCITestService.testFetchSuccessful(localGit, tutor1Login, projectKey1, repositorySlug);

        // Cleanup
        localGit.close();
        FileUtils.deleteDirectory(localRepositoryFolder.toFile());
        remoteGit.close();
        FileUtils.deleteDirectory(remoteRepositoryFolder.toFile());
    }

    // -------- practice mode ----

    @Test
    void testFetchPush_assignmentRepository_student_practiceMode() throws GitAPIException, IOException, URISyntaxException {
        // Create a new practice repository.
        String repositorySlug = projectKey1.toLowerCase() + "-practice-" + student1Login;
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, repositorySlug);
        LocalRepository practiceRepository = new LocalRepository(defaultBranch);
        practiceRepository.configureRepos("localPracticeRepository", remoteRepositoryFolder);
        Git remoteGit = practiceRepository.originGit;
        Path localRepositoryFolder = practiceRepository.localRepoFile.toPath();
        Git localGit = practiceRepository.localGit;

        // Test without participation.
        localVCLocalCITestService.testFetchThrowsException(localGit, student1Login, projectKey1, repositorySlug, internalServerError);
        localVCLocalCITestService.testPushThrowsException(localGit, student1Login, projectKey1, repositorySlug, internalServerError);

        // Create practice participation.
        ProgrammingExerciseStudentParticipation practiceParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, student1Login);
        practiceParticipation.setTestRun(true);
        programmingExerciseStudentParticipationRepository.save(practiceParticipation);

        localVCLocalCITestService.testFetchSuccessful(localGit, student1Login, projectKey1, repositorySlug);

        // Try to access practice repository as student2 who does not own the participation.
        localVCLocalCITestService.testFetchThrowsException(localGit, student2Login, projectKey1, repositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(localGit, student2Login, projectKey1, repositorySlug, notAuthorized);

        // Cleanup
        localGit.close();
        FileUtils.deleteDirectory(localRepositoryFolder.toFile());
        remoteGit.close();
        FileUtils.deleteDirectory(remoteRepositoryFolder.toFile());
        programmingExerciseStudentParticipationRepository.delete(practiceParticipation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFetch_assignmentRepository_student_teamMode() throws GitAPIException, IOException, URISyntaxException {
        programmingExercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(programmingExercise);

        // Create a new team repository.
        String teamShortName = "team1";
        String repositorySlug = projectKey1.toLowerCase() + "-" + teamShortName;
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, repositorySlug);
        LocalRepository repository = new LocalRepository(defaultBranch);
        repository.configureRepos("localTeamRepository", remoteRepositoryFolder);
        Git remoteGit = repository.originGit;
        Path localRepositoryFolder = repository.localRepoFile.toPath();
        Git localGit = repository.localGit;

        // Test without team.
        localVCLocalCITestService.testFetchThrowsException(localGit, student1Login, projectKey1, repositorySlug, internalServerError);
        localVCLocalCITestService.testPushThrowsException(localGit, student1Login, projectKey1, repositorySlug, internalServerError);

        // Create team.
        Team team = new Team();
        team.setName("Team 1");
        team.setShortName(teamShortName);
        team.setExercise(programmingExercise);
        team.setStudents(new HashSet<>(List.of(student1)));
        team.setOwner(student1);
        teamRepository.save(team);

        // Test without participation.
        localVCLocalCITestService.testFetchThrowsException(localGit, student1Login, projectKey1, repositorySlug, internalServerError);
        localVCLocalCITestService.testPushThrowsException(localGit, student1Login, projectKey1, repositorySlug, internalServerError);

        // Create participation.
        ProgrammingExerciseStudentParticipation teamParticipation = database.addTeamParticipationForProgrammingExercise(programmingExercise, team);

        localVCLocalCITestService.testFetchSuccessful(localGit, student1Login, projectKey1, repositorySlug);

        // Try to access the repository as student2, which is not part of the team
        localVCLocalCITestService.testFetchThrowsException(localGit, student2Login, projectKey1, repositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(localGit, student2Login, projectKey1, repositorySlug, notAuthorized);

        // Cleanup
        localGit.close();
        FileUtils.deleteDirectory(localRepositoryFolder.toFile());
        remoteGit.close();
        FileUtils.deleteDirectory(remoteRepositoryFolder.toFile());
        programmingExerciseStudentParticipationRepository.delete(teamParticipation);
        teamRepository.delete(team);
    }

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
        // tutor1 should be able to fetch.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);

        // Due date is in the past.
        exam.setStartDate(ZonedDateTime.now().minusHours(1));
        exam.setEndDate(ZonedDateTime.now().minusMinutes(1));
        examRepository.save(exam);
        studentExam.setExam(exam);
        studentExamRepository.save(studentExam);

        // student1 should not be able to fetch or push.
        localVCLocalCITestService.testFetchThrowsException(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, forbidden);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, forbidden);
        // tutor1 should be able to fetch.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);

        // Cleanup
        programmingExercise.setExerciseGroup(null);
        programmingExerciseRepository.save(programmingExercise);
        studentExamRepository.delete(studentExam);
        examRepository.delete(exam);
        exerciseGroupRepository.delete(exerciseGroup);
    }

    // -------- exam mode ----

    @Test
    void testPush_assignmentRepository_student_examMode() {
        // In time, should succeed.
    }

    @Test
    void testPush_assignmentRepository_teachingAssistant_examMode() {
        // Teaching assistants and up should be able to push to the student's exam repository.
    }

    @Test
    void testPush_assignmentRepository_teachingAssistant_beforeExamStartDate() {
        // Should succeed.
    }

    @Test
    void testPush_assignmentRepository_teachingAssistant_afterExamEndDate() {
        // Should succeed.
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
        // Mock for the solution repository build and for the template repository build that will be triggered as a result of updating the tests.
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
}
