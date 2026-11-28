package de.tum.cit.aet.artemis.assessment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TeamScoreRepository;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

class ParticipantScoreScheduleServiceAthenaTest {

    private final TaskScheduler scheduler = mock(TaskScheduler.class);

    @Test
    void cronDoesNotRefreshScoreOrCompetencyRecencyForPendingAndFailedAthenaRequests() {
        var service = serviceWithModifiedResults(List.of(result(AssessmentType.AUTOMATIC_ATHENA, null), result(AssessmentType.AUTOMATIC_ATHENA, false)));
        service.executeScheduledTasks();
        verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void cronStillSchedulesSuccessfulAthenaAndFailedOrdinaryAutomaticAssessments() {
        var service = serviceWithModifiedResults(List.of(result(AssessmentType.AUTOMATIC_ATHENA, true), result(AssessmentType.AUTOMATIC, false)));
        service.executeScheduledTasks();
        verify(scheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
    }

    private ParticipantScoreScheduleService serviceWithModifiedResults(List<Result> modified) {
        var scores = mock(ParticipantScoreRepository.class);
        when(scores.getLatestModifiedDate()).thenReturn(Optional.of(Instant.now().minusSeconds(1)));
        when(scores.findAllOutdated()).thenReturn(List.of());
        var results = mock(ResultRepository.class);
        when(results.findAllByLastModifiedDateAfter(any())).thenReturn(modified);
        var service = new ParticipantScoreScheduleService(scheduler, Optional.empty(), scores, mock(StudentScoreRepository.class), mock(TeamScoreRepository.class),
                mock(ExerciseRepository.class), results, mock(UserRepository.class), mock(TeamRepository.class));
        service.activate();
        return service;
    }

    private Result result(AssessmentType type, Boolean successful) {
        var student = new User();
        student.setId(3L);
        var exercise = new TextExercise();
        exercise.setId(7L);
        var participation = new StudentParticipation();
        participation.setExercise(exercise);
        participation.setParticipant(student);
        var submission = new TextSubmission();
        submission.setParticipation(participation);
        var result = new Result();
        result.setSubmission(submission);
        result.setAssessmentType(type);
        result.setSuccessful(successful);
        return result;
    }
}
