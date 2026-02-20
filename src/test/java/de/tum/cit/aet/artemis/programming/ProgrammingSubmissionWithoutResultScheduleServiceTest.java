package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionWithoutResultScheduleService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class ProgrammingSubmissionWithoutResultScheduleServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "progsubmissionwithoutresult";

    @Autowired
    private ProgrammingSubmissionWithoutResultScheduleService programmingSubmissionWithoutResultScheduleService;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionTestRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private ProgrammingExerciseStudentParticipation participation1;

    private ProgrammingExerciseStudentParticipation participation2;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 4, 1, 1, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        ProgrammingExercise programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        participation1 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        participation2 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
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

        List<ProgrammingSubmission> allSubmissions = getAllSubmissions(startTime, endTime, pageable);
        assertThat(allSubmissions.stream().map(DomainObject::getId)).anyMatch(submissionInRange.getId()::equals);
        assertThat(allSubmissions.stream().map(ProgrammingSubmission::getCommitHash)).anyMatch("hash1"::equals);
    }

    private @NonNull List<ProgrammingSubmission> getAllSubmissions(ZonedDateTime startTime, ZonedDateTime endTime, Pageable pageable) {
        Slice<ProgrammingSubmission> result = programmingSubmissionTestRepository.findLatestProgrammingSubmissionsWithoutResultsInTimeRange(startTime, endTime, pageable);
        List<ProgrammingSubmission> allSubmissions = new ArrayList<>();
        allSubmissions.addAll(result.getContent());

        while (result.hasNext()) {
            pageable = result.nextPageable();
            result = programmingSubmissionTestRepository.findLatestProgrammingSubmissionsWithoutResultsInTimeRange(startTime, endTime, pageable);
            allSubmissions.addAll(result.getContent());
        }
        return allSubmissions;
    }

    @Test
    void testFindProgrammingSubmissionsWithoutResultsInTimeRangeReturnsOnlyLatestSubmissionPerParticipation() {
        ZonedDateTime now = ZonedDateTime.now();

        ProgrammingSubmission olderSubmissionParticipationOne = createSubmissionWithoutResult(participation1, now.minusHours(8), "hash5");
        ProgrammingSubmission newerSubmissionParticipationOne = createSubmissionWithoutResult(participation1, now.minusHours(6), "hash6");
        ProgrammingSubmission newestSubmissionParticipationOne = createSubmissionWithoutResult(participation1, now.minusHours(3), "hash_newest_p1");

        ProgrammingSubmission submissionParticipationTwo = createSubmissionWithoutResult(participation2, now.minusHours(7), "hash7");

        programmingSubmissionTestRepository
                .saveAll(List.of(olderSubmissionParticipationOne, newerSubmissionParticipationOne, newestSubmissionParticipationOne, submissionParticipationTwo));

        ZonedDateTime startTime = now.minusDays(2);
        ZonedDateTime endTime = now.minusHours(5);
        Pageable pageable = PageRequest.of(0, 10);

        List<ProgrammingSubmission> allSubmissions = getAllSubmissions(startTime, endTime, pageable);
        List<String> commitHashes = allSubmissions.stream().map(ProgrammingSubmission::getCommitHash).toList();
        assertThat(commitHashes).contains("hash7");
        assertThat(commitHashes).doesNotContain("hash5", "hash6", "hash_newest_p1");
    }

    @Test
    void testRetriggerSubmissionsWithoutResultsOnlyForLatestSubmission() {
        ZonedDateTime now = ZonedDateTime.now();

        ProgrammingSubmission olderSubmissionParticipationOne = createSubmissionWithoutResult(participation1, now.minusHours(10), "hash8");
        ProgrammingSubmission newerSubmissionParticipationOne = createSubmissionWithoutResult(participation1, now.minusHours(8), "hash9");

        ProgrammingSubmission latestSubmissionParticipationTwo = createSubmissionWithoutResult(participation2, now.minusHours(9), "hash10");

        programmingSubmissionTestRepository.saveAll(List.of(olderSubmissionParticipationOne, newerSubmissionParticipationOne, latestSubmissionParticipationTwo));

        programmingSubmissionWithoutResultScheduleService.retriggerSubmissionsWithoutResults();

        verify(programmingTriggerService).triggerBuildAndNotifyUser(eq(latestSubmissionParticipationTwo));
        verify(programmingTriggerService).triggerBuildAndNotifyUser(eq(newerSubmissionParticipationOne));
    }

    @Test
    void testRetriggerSubmissionsWithoutResultsNoSubmissionsInRange() {
        ZonedDateTime now = ZonedDateTime.now();

        ProgrammingSubmission tooRecent = createSubmissionWithoutResult(participation1, now.minusMinutes(10), "hash10");
        ProgrammingSubmission tooOld = createSubmissionWithoutResult(participation2, now.minusDays(6), "hash11");
        programmingSubmissionTestRepository.saveAll(List.of(tooRecent, tooOld));

        programmingSubmissionWithoutResultScheduleService.retriggerSubmissionsWithoutResults();

        verify(programmingTriggerService, never()).triggerBuildAndNotifyUser(eq(tooOld));
        verify(programmingTriggerService, never()).triggerBuildAndNotifyUser(eq(tooRecent));
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
