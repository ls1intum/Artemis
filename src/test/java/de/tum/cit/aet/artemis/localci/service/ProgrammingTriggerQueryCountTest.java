package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.dto.ParticipationBuildTriggerDTO;
import de.tum.cit.aet.artemis.programming.service.ProgrammingTriggerService;

/**
 * Guards the cost of triggering builds for many participations of one exercise, which is what an instructor's
 * "build all" and the build-and-test-after-due-date schedule do.
 * <p>
 * What matters here is not the total but the slope: neither reading the participations nor triggering them may grow
 * with the number of participations beyond the one insert each build job needs. Both used to. Reading them cost a query
 * per participation because their eager student association was resolved one at a time, and triggering them cost three
 * because everything the trigger reads off the exercise was resolved per student.
 */
class ProgrammingTriggerQueryCountTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "localcitriggercount";

    /**
     * Loading the exercise with its build config and auxiliary repositories, the course it eagerly brings with it, and
     * the exercise's build statistics. All three are per exercise, not per participation.
     */
    private static final int PER_EXERCISE_QUERY_COUNT = 3;

    /**
     * Only the exercise's build statistics: the caller of the projection based path hands over an exercise it already
     * loaded, so that load is not part of this measurement.
     */
    private static final int PER_EXERCISE_QUERY_COUNT_WITH_LOADED_EXERCISE = 1;

    /** The insert of the build job itself, which is the only unavoidable per-participation write. */
    private static final int PER_PARTICIPATION_QUERY_COUNT = 1;

    /**
     * Reading the trigger inputs of a whole exercise is a single projection query. It must stay one no matter how many
     * participations the exercise has: fetching the participations as entities instead made Hibernate resolve their
     * eager student association one participation at a time.
     */
    private static final int OPENING_QUERY_COUNT = 1;

    @Autowired
    private ProgrammingTriggerService programmingTriggerService;

    private LocalVCTestRepository testsRepo;

    private final List<LocalVCTestRepository> studentRepos = new ArrayList<>();

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeEach
    void setUpRepositories() throws Exception {
        sharedQueueProcessingService.removeListenerAndCancelScheduledFuture();
        sharedQueueProcessingService.setPauseState(true);
        testsRepo = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, testsRepositorySlug);
        localVCLocalCITestService.commitFile(testsRepo.workingCopyPath(), testsRepo.workingCopy());
        testsRepo.workingCopy().push().call();
    }

    @AfterEach
    void removeRepositories() throws Exception {
        for (LocalVCTestRepository repository : studentRepos) {
            repository.deleteWorkingCopy();
        }
        studentRepos.clear();
        testsRepo.deleteWorkingCopy();
        sharedQueueProcessingService.setPauseState(false);
        sharedQueueProcessingService.init();
    }

    /**
     * The regression this pins is the one that dominated the call: the trigger inputs of every participation of the
     * exercise come back in one query, so opening a "build all" does not cost a round trip per student.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void readingTheTriggerInputsOfAnExerciseIsOneQuery() throws Exception {
        createParticipationsWithSubmissions(4);

        var triggerData = assertThatDb(() -> programmingExerciseStudentParticipationRepository.findBuildTriggerDataByExerciseId(programmingExercise.getId()))
                .hasBeenCalledAtMostTimes(OPENING_QUERY_COUNT);

        // The projection has to be complete, otherwise the trigger would read null where it needs a value.
        assertThat(triggerData).hasSize(4).allSatisfy(data -> {
            assertThat(data.studentLogin()).isNotNull();
            assertThat(data.repositoryUri()).isNotNull();
            assertThat(data.buildPlanId()).isNotNull();
            assertThat(data.commitHash()).isNotNull();
            assertThat(data.submissionId()).isPositive();
            assertThat(data.needsResume()).isFalse();
        });
    }

    /**
     * The detached participation the projection builds has to expose everything the trigger path reads off a
     * participation, because a missing field would surface as a websocket error for that student rather than as a
     * failure here. The listed fields are the ones the trigger, the build job, the websocket notification and, for an
     * exam exercise, the individual working period check actually look at.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void theDetachedParticipationCarriesEverythingTheTriggerReads() throws Exception {
        createParticipationsWithSubmissions(1);
        var exercise = programmingExerciseRepository.findWithBuildConfigAndAuxiliaryRepositoriesById(programmingExercise.getId()).orElseThrow();
        var data = programmingExerciseStudentParticipationRepository.findBuildTriggerDataByExerciseId(programmingExercise.getId()).getFirst();

        var participation = data.toDetachedParticipation(exercise);

        assertThat(participation.getId()).isEqualTo(data.participationId());
        assertThat(participation.getRepositoryUri()).isEqualTo(data.repositoryUri());
        assertThat(participation.getVcsRepositoryUri()).isNotNull();
        assertThat(participation.getBuildPlanId()).isEqualTo(data.buildPlanId());
        assertThat(participation.getBranch()).isEqualTo(data.branch());
        assertThat(participation.getInitializationState()).isEqualTo(data.initializationState());
        assertThat(participation.getProgrammingExercise()).isSameAs(exercise);
        assertThat(participation.getExercise()).isSameAs(exercise);
        // The websocket notification addresses the owner by login, and the exam working period check reads its id.
        assertThat(participation.getStudents()).singleElement().satisfies(student -> {
            assertThat(student.getLogin()).isEqualTo(data.studentLogin());
            assertThat(student.getId()).isEqualTo(data.studentId());
        });
        assertThat(participation.getParticipant()).isNotNull();
        assertThat(participation.getParticipant().getId()).isEqualTo(data.studentId());
        assertThat(participation.findLatestSubmission()).get().satisfies(submission -> {
            assertThat(submission.getId()).isEqualTo(data.submissionId());
            assertThat(((ProgrammingSubmission) submission).getCommitHash()).isEqualTo(data.commitHash());
            assertThat(submission.getParticipation()).isSameAs(participation);
        });
    }

    /**
     * Resolving the shared inputs pays off even for a single participation, which is what the individual due date
     * schedule triggers: one load of the exercise with its build config and auxiliary repositories costs less than the
     * separate lookups the trigger would otherwise make. Measured, this case went from five queries to four.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggeringOneParticipationResolvesThePerExerciseWorkOnce() throws Exception {
        var participations = participationEntitiesWithSubmissions(1);

        assertThatDb(() -> {
            programmingTriggerService.triggerBuildForParticipations(participations);
            return null;
        }).hasBeenCalledAtMostTimes(PER_EXERCISE_QUERY_COUNT + PER_PARTICIPATION_QUERY_COUNT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggeringManyParticipationsOnlyAddsOneQueryEach() throws Exception {
        int participationCount = 4;
        var participations = participationEntitiesWithSubmissions(participationCount);

        // The point of the assertion: the per-exercise part does not multiply. Before the batch resolved the build
        // config, the auxiliary repositories and the build statistics once, this was three queries per participation.
        assertThatDb(() -> {
            programmingTriggerService.triggerBuildForParticipations(participations);
            return null;
        }).hasBeenCalledAtMostTimes(PER_EXERCISE_QUERY_COUNT + participationCount * PER_PARTICIPATION_QUERY_COUNT);
    }

    /**
     * The path an instructor's "build all" takes. The projection holds everything the trigger reads, so no participation
     * is loaded again and the only query per participation is the insert of its build job.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void triggeringFromTheProjectionOnlyAddsOneQueryEach() throws Exception {
        int participationCount = 4;
        List<ParticipationBuildTriggerDTO> triggerData = createParticipationsWithSubmissions(participationCount);
        var exercise = programmingExerciseRepository.findWithBuildConfigAndAuxiliaryRepositoriesById(programmingExercise.getId()).orElseThrow();

        assertThatDb(() -> {
            programmingTriggerService.triggerBuildForParticipationData(triggerData, exercise);
            return null;
        }).hasBeenCalledAtMostTimes(PER_EXERCISE_QUERY_COUNT_WITH_LOADED_EXERCISE + participationCount * PER_PARTICIPATION_QUERY_COUNT);
    }

    private List<ParticipationBuildTriggerDTO> createParticipationsWithSubmissions(int count) throws Exception {
        userUtilService.addStudents(TEST_PREFIX, 1, count);
        for (int i = 1; i <= count; i++) {
            String login = TEST_PREFIX + "student" + i;
            var participation = localVCLocalCITestService.createParticipation(programmingExercise, login);
            LocalVCTestRepository repository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1,
                    localVCLocalCITestService.getRepositorySlug(projectKey1, login));
            studentRepos.add(repository);
            localVCLocalCITestService.commitFile(repository.workingCopyPath(), repository.workingCopy());
            repository.workingCopy().push().call();
            programmingExerciseUtilService.createProgrammingSubmission(participation, false);
        }
        // Read them back exactly the way the production trigger-all path does.
        return programmingExerciseStudentParticipationRepository.findBuildTriggerDataByExerciseId(programmingExercise.getId());
    }

    private List<ProgrammingExerciseStudentParticipation> participationEntitiesWithSubmissions(int count) throws Exception {
        createParticipationsWithSubmissions(count);
        return new ArrayList<>(programmingExerciseStudentParticipationRepository.findWithSubmissionsAndTeamStudentsByExerciseId(programmingExercise.getId()));
    }
}
