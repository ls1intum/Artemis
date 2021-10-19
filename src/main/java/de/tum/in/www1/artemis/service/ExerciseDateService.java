package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ExerciseDateService {

    private final ExerciseRepository exerciseRepository;

    private final ParticipationRepository participationRepository;

    private final ExamDateService examDateService;

    public ExerciseDateService(ExerciseRepository exerciseRepository, ParticipationRepository participationRepository, ExamDateService examDateService) {
        this.exerciseRepository = exerciseRepository;
        this.participationRepository = participationRepository;
        this.examDateService = examDateService;
    }

    /**
     * Finds the latest individual due date for participants. If no individual due dates exist, then the exercise due date is returned.
     *
     * @param exerciseId of the exercise for which the latest due date should be returned.
     * @return the latest individual due date, or if not existing the exercise due date.
     * @throws EntityNotFoundException if no exercise with the given id can be found.
     */
    public ZonedDateTime getLatestIndividualDueDate(Long exerciseId) {
        final Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        return getLatestIndividualDueDate(exercise);
    }

    /**
     * Finds the latest individual due date for participants. If no individual due dates exist, then the exercise due date is returned.
     *
     * @param exercise the exercise for which the latest due date should be returned.
     * @return the latest individual due date, or if not existing the exercise due date.
     */
    public ZonedDateTime getLatestIndividualDueDate(Exercise exercise) {
        return participationRepository.findLatestIndividualDueDate(exercise.getId()).orElse(exercise.getDueDate());
    }

    /**
     * Checks if submissions are no longer possible.
     *
     * Checks for exam or course exercise, and if an individual due date is set for the given participation or only a course-wide due date applies.
     * @param participation in a course or exam exercise.
     * @return true, if the due date is in the past and submissions are no longer possible.
     */
    public boolean isAfterDueDate(Participation participation) {
        final Exercise exercise = participation.getExercise();
        if (exercise.isExamExercise()) {
            return examDateService.isExerciseWorkingPeriodOver(exercise);
        }

        final ZonedDateTime now = ZonedDateTime.now();

        if (participation.getIndividualDueDate() != null) {
            return now.isAfter(participation.getIndividualDueDate());
        }
        else if (exercise.getDueDate() != null) {
            return now.isAfter(exercise.getDueDate());
        }
        else {
            return false;
        }
    }
}
