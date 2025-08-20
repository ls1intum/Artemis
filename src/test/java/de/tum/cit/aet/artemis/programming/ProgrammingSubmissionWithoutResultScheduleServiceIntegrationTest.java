package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionWithoutResultScheduleService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class ProgrammingSubmissionWithoutResultScheduleServiceIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "progsubmissionwithoutresult";

    @Autowired
    private ProgrammingSubmissionWithoutResultScheduleService programmingSubmissionWithoutResultScheduleService;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionTestRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private ProgrammingExercise programmingExercise;

    private ProgrammingExerciseStudentParticipation participation1;

    private ProgrammingExerciseStudentParticipation participation2;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 4, 1, 1, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        participation1 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        participation2 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
    }

    @AfterEach
    void cleanup() {
        programmingSubmissionTestRepository.deleteAll();
    }

    @Test
    void testFindProgrammingSubmissionsWithoutResultsInTimeRange() {
        ZonedDateTime now = ZonedDateTime.now();

        ProgrammingSubmission submissionInRange = createSubmissionWithoutResult(participation1, now.minusHours(8), "hash1");

        ProgrammingSubmission submissionTooRecent = createSubmissionWithoutResult(participation2, now.minusHours(2), "hash2");

        ProgrammingSubmission submissionTooOld = createSubmissionWithoutResult(participation1, now.minusDays(3), "hash3");

        programmingSubmissionTestRepository.saveAll(List.of(submissionInRange, submissionTooRecent, submissionTooOld));

        ZonedDateTime startTime = now.minusDays(2);
        ZonedDateTime endTime = now.minusHours(5);
        Pageable pageable = PageRequest.of(0, 10);

        Slice<ProgrammingSubmission> result = programmingSubmissionRepository.findProgrammingSubmissionsWithoutResultsInTimeRange(startTime, endTime, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getId()).isEqualTo(submissionInRange.getId());
        assertThat(result.getContent().getFirst().getCommitHash()).isEqualTo("hash1");
    }

    @Test
    void testFindProgrammingSubmissionsWithoutResultsInTimeRangeReturnsOnlyLatestSubmissionPerParticipation() {
        ZonedDateTime now = ZonedDateTime.now();

        ProgrammingSubmission olderSubmissionParticipationOne = createSubmissionWithoutResult(participation1, now.minusHours(8), "hash5");
        ProgrammingSubmission newerSubmissionParticipationOne = createSubmissionWithoutResult(participation1, now.minusHours(6), "hash6");

        ProgrammingSubmission submissionParticipationTwo = createSubmissionWithoutResult(participation2, now.minusHours(7), "hash7");

        programmingSubmissionTestRepository.saveAll(List.of(olderSubmissionParticipationOne, newerSubmissionParticipationOne, submissionParticipationTwo));

        ZonedDateTime startTime = now.minusDays(2);
        ZonedDateTime endTime = now.minusHours(5);
        Pageable pageable = PageRequest.of(0, 10);

        Slice<ProgrammingSubmission> result = programmingSubmissionRepository.findProgrammingSubmissionsWithoutResultsInTimeRange(startTime, endTime, pageable);

        assertThat(result.getContent()).hasSize(2);

        List<String> commitHashes = result.getContent().stream().map(ProgrammingSubmission::getCommitHash).toList();
        assertThat(commitHashes).containsExactlyInAnyOrder("hash6", "hash7");
        assertThat(commitHashes).doesNotContain("hash5");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRetriggerSubmissionsWithoutResultsOnlyForLatestSubmission() {
        ZonedDateTime now = ZonedDateTime.now();

        ProgrammingSubmission olderSubmissionParticipationOne = createSubmissionWithoutResult(participation1, now.minusHours(10), "hash8");
        ProgrammingSubmission newerSubmissionParticipationOne = createSubmissionWithoutResult(participation1, now.minusHours(8), "hash9");

        ProgrammingSubmission latestSubmissionParticipationTwo = createSubmissionWithoutResult(participation2, now.minusHours(9), "hash10");

        programmingSubmissionTestRepository.saveAll(List.of(olderSubmissionParticipationOne, newerSubmissionParticipationOne, latestSubmissionParticipationTwo));

        programmingSubmissionWithoutResultScheduleService.retriggerSubmissionsWithoutResults();

        verify(programmingTriggerService, times(2)).triggerBuildAndNotifyUser(any(ProgrammingSubmission.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRetriggerSubmissionsWithoutResultsNoSubmissionsInRange() {
        ZonedDateTime now = ZonedDateTime.now();

        // Too recent
        ProgrammingSubmission tooRecent = createSubmissionWithoutResult(participation1, now.minusHours(2), "hash10");
        // Too old
        ProgrammingSubmission tooOld = createSubmissionWithoutResult(participation2, now.minusDays(6), "hash11");
        programmingSubmissionTestRepository.saveAll(List.of(tooRecent, tooOld));

        programmingSubmissionWithoutResultScheduleService.retriggerSubmissionsWithoutResults();

        verify(programmingTriggerService, never()).triggerBuildAndNotifyUser(any(ProgrammingSubmission.class));
    }

    @Test
    void testFindProgrammingSubmissionsWithoutResultsInTimeRangeReturnsEmptyResult() {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime startTime = now.minusDays(2);
        ZonedDateTime endTime = now.minusHours(5);
        Pageable pageable = PageRequest.of(0, 10);

        Slice<ProgrammingSubmission> result = programmingSubmissionRepository.findProgrammingSubmissionsWithoutResultsInTimeRange(startTime, endTime, pageable);

        // Then: Result should be empty
        assertThat(result.getContent()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void testFindProgrammingSubmissionsWithoutResultsInTimeRangePaginationWorks() {
        ZonedDateTime now = ZonedDateTime.now();

        ProgrammingExerciseStudentParticipation participation3 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student3");
        ProgrammingExerciseStudentParticipation participation4 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student4");

        List<ProgrammingSubmission> submissions = List.of(createSubmissionWithoutResult(participation1, now.minusHours(6), "hash_p1_latest"),
                createSubmissionWithoutResult(participation2, now.minusHours(7), "hash_p2_latest"),
                createSubmissionWithoutResult(participation3, now.minusHours(8), "hash_p3_latest"),
                createSubmissionWithoutResult(participation4, now.minusHours(9), "hash_p4_latest"));
        programmingSubmissionTestRepository.saveAll(submissions);

        ZonedDateTime startTime = now.minusDays(2);
        ZonedDateTime endTime = now.minusHours(5);
        Pageable pageable = PageRequest.of(0, 2);

        Slice<ProgrammingSubmission> firstPage = programmingSubmissionRepository.findProgrammingSubmissionsWithoutResultsInTimeRange(startTime, endTime, pageable);

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.hasNext()).isTrue();

        Slice<ProgrammingSubmission> secondPage = programmingSubmissionRepository.findProgrammingSubmissionsWithoutResultsInTimeRange(startTime, endTime, firstPage.nextPageable());

        assertThat(secondPage.getContent()).hasSize(2);
        assertThat(secondPage.hasNext()).isFalse();

        List<String> allCommitHashes = firstPage.getContent().stream().map(ProgrammingSubmission::getCommitHash).collect(Collectors.toCollection(ArrayList::new));
        allCommitHashes.addAll(secondPage.getContent().stream().map(ProgrammingSubmission::getCommitHash).toList());
        assertThat(allCommitHashes).containsExactlyInAnyOrder("hash_p1_latest", "hash_p2_latest", "hash_p3_latest", "hash_p4_latest");
    }

    private ProgrammingSubmission createSubmissionWithoutResult(ProgrammingExerciseStudentParticipation participation, ZonedDateTime submissionDate, String commitHash) {
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setParticipation(participation);
        submission.setSubmissionDate(submissionDate);
        submission.setCommitHash(commitHash);
        submission.setType(SubmissionType.MANUAL);
        submission.setSubmitted(true);
        return submission;
    }
}
