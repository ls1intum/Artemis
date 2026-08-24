package de.tum.cit.aet.artemis.localci.service;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.service.ProgrammingTriggerService;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;

/**
 * Guards the cost of triggering builds for many participations of one exercise, which is what an instructor's
 * "build all" and the build-and-test-after-due-date schedule do.
 * <p>
 * What matters here is not the total but the slope: everything a trigger reads off the exercise rather than off the
 * participation has to be resolved once for the batch, so the count must grow by the one insert each build job needs
 * and by nothing else. It used to grow by three, which is what made the call spike the node handling it.
 */
class ProgrammingTriggerQueryCountTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "localcitriggercount";

    /**
     * Loading the exercise with its build config and auxiliary repositories, the course it eagerly brings with it, and
     * the exercise's build statistics. All three are per exercise, not per participation.
     */
    private static final int PER_EXERCISE_QUERY_COUNT = 3;

    /** The insert of the build job itself, which is the only unavoidable per-participation write. */
    private static final int PER_PARTICIPATION_QUERY_COUNT = 1;

    @Autowired
    private ProgrammingTriggerService programmingTriggerService;

    private LocalRepository testsRepo;

    private final List<LocalRepository> studentRepos = new ArrayList<>();

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeEach
    void setUpRepositories() throws Exception {
        sharedQueueProcessingService.removeListenerAndCancelScheduledFuture();
        sharedQueueProcessingService.setPauseState(true);
        testsRepo = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, testsRepositorySlug);
        localVCLocalCITestService.commitFile(testsRepo.workingCopyGitRepoFile.toPath(), testsRepo.workingCopyGitRepo);
        testsRepo.workingCopyGitRepo.push().call();
    }

    @AfterEach
    void removeRepositories() throws Exception {
        for (LocalRepository repository : studentRepos) {
            repository.resetLocalRepo();
        }
        studentRepos.clear();
        testsRepo.resetLocalRepo();
        sharedQueueProcessingService.setPauseState(false);
        sharedQueueProcessingService.init();
    }

    /**
     * Resolving the shared inputs pays off even for a single participation, which is what the individual due date
     * schedule triggers: one load of the exercise with its build config and auxiliary repositories costs less than the
     * separate lookups the trigger would otherwise make. Measured, this case went from five queries to four.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggeringOneParticipationResolvesThePerExerciseWorkOnce() throws Exception {
        var participations = participationsWithSubmissions(1);

        assertThatDb(() -> {
            programmingTriggerService.triggerBuildForParticipations(participations);
            return null;
        }).hasBeenCalledAtMostTimes(PER_EXERCISE_QUERY_COUNT + PER_PARTICIPATION_QUERY_COUNT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggeringManyParticipationsOnlyAddsOneQueryEach() throws Exception {
        int participationCount = 4;
        var participations = participationsWithSubmissions(participationCount);

        // The point of the assertion: the per-exercise part does not multiply. Before the batch resolved the build
        // config, the auxiliary repositories and the build statistics once, this was three queries per participation.
        assertThatDb(() -> {
            programmingTriggerService.triggerBuildForParticipations(participations);
            return null;
        }).hasBeenCalledAtMostTimes(PER_EXERCISE_QUERY_COUNT + participationCount * PER_PARTICIPATION_QUERY_COUNT);
    }

    private List<ProgrammingExerciseStudentParticipation> participationsWithSubmissions(int count) throws Exception {
        userUtilService.addStudents(TEST_PREFIX, 1, count);
        for (int i = 1; i <= count; i++) {
            String login = TEST_PREFIX + "student" + i;
            var participation = localVCLocalCITestService.createParticipation(programmingExercise, login);
            LocalRepository repository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, localVCLocalCITestService.getRepositorySlug(projectKey1, login));
            studentRepos.add(repository);
            localVCLocalCITestService.commitFile(repository.workingCopyGitRepoFile.toPath(), repository.workingCopyGitRepo);
            repository.workingCopyGitRepo.push().call();
            programmingExerciseUtilService.createProgrammingSubmission(participation, false);
        }
        // Read them back exactly the way the production trigger-all path does.
        return new ArrayList<>(programmingExerciseStudentParticipationRepository.findWithLatestSubmissionByExerciseId(programmingExercise.getId()));
    }
}
