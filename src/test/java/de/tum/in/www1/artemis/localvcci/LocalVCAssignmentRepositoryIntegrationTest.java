package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
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
import de.tum.in.www1.artemis.util.LocalRepository;

/**
 * This class contains integration tests requesting the assignment repository that should not touch the local CI system (i.e. either fetch requests or failing push requests).
 */
class LocalVCAssignmentRepositoryIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    protected Path remoteAssignmentRepositoryFolder;

    protected Git remoteAssignmentGit;

    protected Path localAssignmentRepositoryFolder;

    protected Git localAssignmentGit;

    @BeforeEach
    void initRepository() throws GitAPIException, IOException, URISyntaxException {
        // Create remote assignment repository
        remoteAssignmentRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, assignmentRepositoryName);
        LocalRepository assignmentRepository = new LocalRepository(defaultBranch);
        assignmentRepository.configureRepos("localAssignment", remoteAssignmentRepositoryFolder);
        remoteAssignmentGit = assignmentRepository.originGit;
        localAssignmentRepositoryFolder = assignmentRepository.localRepoFile.toPath();
        localAssignmentGit = assignmentRepository.localGit;
    }

    @AfterEach
    void removeRepositories() throws IOException {
        if (remoteAssignmentGit != null) {
            remoteAssignmentGit.close();
        }
        if (localAssignmentGit != null) {
            localAssignmentGit.close();
        }
        if (remoteAssignmentRepositoryFolder != null && Files.exists(remoteAssignmentRepositoryFolder)) {
            FileUtils.deleteDirectory(remoteAssignmentRepositoryFolder.toFile());
        }
        if (localAssignmentRepositoryFolder != null && Files.exists(localAssignmentRepositoryFolder)) {
            FileUtils.deleteDirectory(localAssignmentRepositoryFolder.toFile());
        }
    }

    @Test
    void testFetch_repositoryDoesNotExist() {
        String repositoryUrl = localVCLocalCITestService.constructLocalVCUrl(student1Login, "SOMENONEXISTENTPROJECTKEY", "some-nonexistent-repository-slug");
        Exception exception = assertThrows(InvalidRemoteException.class, () -> {
            try (Git ignored = Git.cloneRepository().setURI(repositoryUrl).call()) {
                fail("The clone operation should have failed.");
            }
        });
        assertThat(exception.getCause().getMessage()).contains("not found");
    }

    @Test
    void testPush_repositoryDoesNotExist() throws IOException, GitAPIException, URISyntaxException {
        // Create a new repository, delete the remote repository and try to push to the remote repository.
        String projectKey = "SOMEPROJECTKEY";
        String repositorySlug = "some-repository-slug";
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, repositorySlug);
        LocalRepository someRepository = new LocalRepository(defaultBranch);
        someRepository.configureRepos("localRepository", remoteRepositoryFolder);
        Git remoteGit = someRepository.originGit;
        Path localRepositoryFolder = someRepository.localRepoFile.toPath();
        Git localGit = someRepository.localGit;

        // Delete the remote repository.
        remoteGit.close();
        FileUtils.deleteDirectory(remoteRepositoryFolder.toFile());

        // Try to push to the remote repository.
        localVCLocalCITestService.testPushThrowsException(localGit, "someUser", projectKey, repositorySlug, TransportException.class, notFound);

        // Cleanup
        localGit.close();
        FileUtils.deleteDirectory(localRepositoryFolder.toFile());
    }

    @Test
    void testFetch_wrongCredentials() {
        localVCLocalCITestService.testFetchThrowsException(localAssignmentGit, student1Login, "wrong-password", projectKey1, assignmentRepositoryName, TransportException.class,
                notAuthorized);
    }

    @Test
    void testPush_wrongCredentials() {
        localVCLocalCITestService.testPushThrowsException(localAssignmentGit, student1Login, "wrong-password", projectKey1, assignmentRepositoryName, TransportException.class,
                notAuthorized);
    }

    @Test
    void testFetch_incompleteCredentials() {
        localVCLocalCITestService.testFetchThrowsException(localAssignmentGit, student1Login, "", projectKey1, assignmentRepositoryName, TransportException.class, notAuthorized);
    }

    @Test
    void testPush_incompleteCredentials() {
        localVCLocalCITestService.testPushThrowsException(localAssignmentGit, student1Login, "", projectKey1, assignmentRepositoryName, TransportException.class, notAuthorized);
    }

    @Test
    void testFetchPush_programmingExerciseDoesNotExist() throws GitAPIException, IOException, URISyntaxException {
        // Create a repository for an exercise that does not exist.
        String projectKey = "SOMEPROJECTKEY";
        String repositorySlug = "someprojectkey-some-repository-slug";
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey, repositorySlug);
        LocalRepository someRepository = new LocalRepository(defaultBranch);
        someRepository.configureRepos("localRepository", remoteRepositoryFolder);
        Git remoteGit = someRepository.originGit;
        Path localRepositoryFolder = someRepository.localRepoFile.toPath();
        Git localGit = someRepository.localGit;

        localVCLocalCITestService.testFetchThrowsException(localGit, student1Login, projectKey, repositorySlug, TransportException.class, internalServerError);
        localVCLocalCITestService.testPushThrowsException(localGit, student1Login, projectKey, repositorySlug, TransportException.class, internalServerError);

        // Cleanup
        localGit.close();
        FileUtils.deleteDirectory(localRepositoryFolder.toFile());
        remoteGit.close();
        FileUtils.deleteDirectory(remoteRepositoryFolder.toFile());
    }

    @Test
    void testFetchPush_offlineIDENotAllowed() {
        programmingExercise.setAllowOfflineIde(false);
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testFetchThrowsException(localAssignmentGit, student1Login, projectKey1, assignmentRepositoryName, TransportException.class, forbidden);
        localVCLocalCITestService.testPushThrowsException(localAssignmentGit, student1Login, projectKey1, assignmentRepositoryName, TransportException.class, forbidden);

        programmingExercise.setAllowOfflineIde(true);
        programmingExerciseRepository.save(programmingExercise);
    }

    // ---- Tests for the assignment repository ----

    @Test
    void testFetch_assignmentRepository_student() {
        localVCLocalCITestService.testFetchSuccessful(localAssignmentGit, student1Login, projectKey1, assignmentRepositoryName);
    }

    @Test
    void testFetchPush_assignmentRepository_student_noParticipation() throws GitAPIException, IOException, URISyntaxException {
        // Create a new repository, but don't create a participation for student2.
        String repositorySlug = projectKey1.toLowerCase() + "-" + student2Login;
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, repositorySlug);
        LocalRepository someRepository = new LocalRepository(defaultBranch);
        someRepository.configureRepos("localRepository", remoteRepositoryFolder);
        Git remoteGit = someRepository.originGit;
        Path localRepositoryFolder = someRepository.localRepoFile.toPath();
        Git localGit = someRepository.localGit;

        localVCLocalCITestService.testFetchThrowsException(localGit, student2Login, projectKey1, repositorySlug, TransportException.class, internalServerError);
        localVCLocalCITestService.testPushThrowsException(localGit, student2Login, projectKey1, repositorySlug, TransportException.class, internalServerError);

        // Cleanup
        localGit.close();
        FileUtils.deleteDirectory(localRepositoryFolder.toFile());
        remoteGit.close();
        FileUtils.deleteDirectory(remoteRepositoryFolder.toFile());
    }

    @Test
    void testFetchPush_assignmentRepository_student_studentDoesNotOwnParticipation() {
        // Student2 tries to access the repository of student1.
        localVCLocalCITestService.testFetchThrowsException(localAssignmentGit, student2Login, projectKey1, assignmentRepositoryName, TransportException.class, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(localAssignmentGit, student2Login, projectKey1, assignmentRepositoryName, TransportException.class, notAuthorized);
    }

    @Test
    void testPush_assignmentRepository_student_beforeStartDate() {
        programmingExercise.setStartDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testPushThrowsException(localAssignmentGit, student1Login, projectKey1, assignmentRepositoryName, TransportException.class, forbidden);
    }

    @Test
    void testFetchPush_assignmentRepository_student_afterDueDate() {
        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(1));
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testFetchSuccessful(localAssignmentGit, student1Login, projectKey1, assignmentRepositoryName);
        localVCLocalCITestService.testPushThrowsException(localAssignmentGit, student1Login, projectKey1, assignmentRepositoryName, TransportException.class, forbidden);
    }

    @Test
    void testFetchPush_assignmentRepository_teachingAssistant() {
        // Teaching assistants should always be able to fetch student assignment repositories.
        localVCLocalCITestService.testFetchSuccessful(localAssignmentGit, tutor1Login, projectKey1, assignmentRepositoryName);
        localVCLocalCITestService.testPushThrowsException(localAssignmentGit, tutor1Login, projectKey1, assignmentRepositoryName, TransportException.class, notAuthorized);

        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(1));
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testFetchSuccessful(localAssignmentGit, tutor1Login, projectKey1, assignmentRepositoryName);
        localVCLocalCITestService.testPushThrowsException(localAssignmentGit, tutor1Login, projectKey1, assignmentRepositoryName, TransportException.class, notAuthorized);
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
        localVCLocalCITestService.testFetchThrowsException(localGit, student1Login, projectKey1, repositorySlug, TransportException.class, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(localGit, student1Login, projectKey1, repositorySlug, TransportException.class, notAuthorized);

        // Instructor
        localVCLocalCITestService.testFetchSuccessful(localGit, instructorLogin, projectKey1, repositorySlug);

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
        localVCLocalCITestService.testFetchThrowsException(localGit, student1Login, projectKey1, repositorySlug, TransportException.class, internalServerError);
        localVCLocalCITestService.testPushThrowsException(localGit, student1Login, projectKey1, repositorySlug, TransportException.class, internalServerError);

        // Create practice participation.
        ProgrammingExerciseStudentParticipation practiceParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, student1Login);
        practiceParticipation.setTestRun(true);
        programmingExerciseStudentParticipationRepository.save(practiceParticipation);

        localVCLocalCITestService.testFetchSuccessful(localGit, student1Login, projectKey1, repositorySlug);

        // Try to access practice repository as student2 who does not own the participation.
        localVCLocalCITestService.testFetchThrowsException(localGit, student2Login, projectKey1, repositorySlug, TransportException.class, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(localGit, student2Login, projectKey1, repositorySlug, TransportException.class, notAuthorized);

        // Cleanup
        localGit.close();
        FileUtils.deleteDirectory(localRepositoryFolder.toFile());
        remoteGit.close();
        FileUtils.deleteDirectory(remoteRepositoryFolder.toFile());
        programmingExerciseStudentParticipationRepository.delete(practiceParticipation);
    }

    // -------- team mode ----

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
        localVCLocalCITestService.testFetchThrowsException(localGit, student1Login, projectKey1, repositorySlug, TransportException.class, internalServerError);
        localVCLocalCITestService.testPushThrowsException(localGit, student1Login, projectKey1, repositorySlug, TransportException.class, internalServerError);

        // Create team.
        Team team = new Team();
        team.setName("Team 1");
        team.setShortName(teamShortName);
        team.setExercise(programmingExercise);
        team.setStudents(new HashSet<>(List.of(student1)));
        team.setOwner(student1);
        teamRepository.save(team);

        // Test without participation.
        localVCLocalCITestService.testFetchThrowsException(localGit, student1Login, projectKey1, repositorySlug, TransportException.class, internalServerError);
        localVCLocalCITestService.testPushThrowsException(localGit, student1Login, projectKey1, repositorySlug, TransportException.class, internalServerError);

        // Create participation.
        ProgrammingExerciseStudentParticipation teamParticipation = database.addTeamParticipationForProgrammingExercise(programmingExercise, team);

        localVCLocalCITestService.testFetchSuccessful(localGit, student1Login, projectKey1, repositorySlug);

        // Try to access the repository as student2, which is not part of the team
        localVCLocalCITestService.testFetchThrowsException(localGit, student2Login, projectKey1, repositorySlug, TransportException.class, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(localGit, student2Login, projectKey1, repositorySlug, TransportException.class, notAuthorized);

        // Cleanup
        localGit.close();
        FileUtils.deleteDirectory(localRepositoryFolder.toFile());
        remoteGit.close();
        FileUtils.deleteDirectory(remoteRepositoryFolder.toFile());
        programmingExerciseStudentParticipationRepository.delete(teamParticipation);
        teamRepository.delete(team);
    }

    // -------- exam mode ----

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
        localVCLocalCITestService.testFetchThrowsException(localAssignmentGit, student1Login, projectKey1, assignmentRepositoryName, TransportException.class, forbidden);
        localVCLocalCITestService.testPushThrowsException(localAssignmentGit, student1Login, projectKey1, assignmentRepositoryName, TransportException.class, forbidden);
        // tutor1 should be able to fetch.
        localVCLocalCITestService.testFetchSuccessful(localAssignmentGit, tutor1Login, projectKey1, assignmentRepositoryName);

        // Due date is in the past.
        exam.setStartDate(ZonedDateTime.now().minusHours(1));
        exam.setEndDate(ZonedDateTime.now().minusMinutes(1));
        examRepository.save(exam);
        studentExam.setExam(exam);
        studentExamRepository.save(studentExam);

        // student1 should not be able to fetch or push.
        localVCLocalCITestService.testFetchThrowsException(localAssignmentGit, student1Login, projectKey1, assignmentRepositoryName, TransportException.class, forbidden);
        localVCLocalCITestService.testPushThrowsException(localAssignmentGit, student1Login, projectKey1, assignmentRepositoryName, TransportException.class, forbidden);
        // tutor1 should be able to fetch.
        localVCLocalCITestService.testFetchSuccessful(localAssignmentGit, tutor1Login, projectKey1, assignmentRepositoryName);

        // Cleanup
        programmingExercise.setExerciseGroup(null);
        programmingExerciseRepository.save(programmingExercise);
        studentExamRepository.delete(studentExam);
        examRepository.delete(exam);
        exerciseGroupRepository.delete(exerciseGroup);
    }

    // ---- Tests for the solution repository ----

    @Test
    void testFetch_solutionRepository_teachingAssistant() {
        // Should be successful
    }

    @Test
    void testFetch_solutionRepository_teachingAssistant_noParticipation() {

    }

    @Test
    void testPush_solutionRepository_teachingAssistant_noParticipation() {

    }

    @Test
    void testFetch_solutionRepository_student() {
        // The tests repository can only be fetched by users that are at least teaching assistants in the course.
    }

    @Test
    void testPush_solutionRepository_student() {
        // The tests repository can only be pushed to by users that are at least teaching assistants in the course.
    }

    // ---- Tests for the template repository ----

    @Test
    void testFetch_templateRepository_teachingAssistant() {
        // Should be successful
    }

    @Test
    void testFetch_templateRepository_teachingAssistant_noParticipation() {

    }

    @Test
    void testPush_templateRepository_teachingAssistant_noParticipation() {

    }

    @Test
    void testFetch_templateRepository_student() {
        // The tests repository can only be fetched by users that are at least teaching assistants in the course.
    }

    @Test
    void testPush_templateRepository_student() {
        // The tests repository can only be pushed to by users that are at least teaching assistants in the course.
    }

    // ---- Edge cases ----

    @Test
    void testUserTriesToDeleteBranch() {

    }

    @Test
    void testUserTriesToForcePush() {

    }

    @Test
    void testUserTriesToRenameBranch() {

    }
}
