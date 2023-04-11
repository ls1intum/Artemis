package de.tum.in.www1.artemis.localvcci;

import java.nio.file.Path;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.util.LocalRepository;

public class LocalVCLocalCIPracticeIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

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
