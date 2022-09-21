package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.participation.ParticipationInterface;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.service.exam.ExamDateService;

@Service
public class ExerciseDateService {

    private final ParticipationRepository participationRepository;

    private final ExamDateService examDateService;

    public ExerciseDateService(ParticipationRepository participationRepository, ExamDateService examDateService) {
        this.participationRepository = participationRepository;
        this.examDateService = examDateService;
    }

    /**
     * Finds the latest individual due date for participants. If no individual due dates exist, then the exercise due date is returned.
     * Returns nothing if the exercise itself has no due date.
     * @param exercise the exercise for which the latest due date should be returned.
     * @return the latest individual due date, or if not existing the exercise due date.
     */
    public Optional<ZonedDateTime> getLatestIndividualDueDate(Exercise exercise) {
        if (exercise.getDueDate() == null) {
            // early exit to avoid database call, same result would be produced
            // in Optional.ofNullable(exercise.getDueDate()) below
            return Optional.empty();
        }
        return participationRepository.findLatestIndividualDueDate(exercise.getId()).or(() -> Optional.ofNullable(exercise.getDueDate()));
    }

    /**
     * Finds the earliest individual due date for participants.
     * Returns nothing if the exercise itself has no due date.
     * @param exercise the exercise for which the earliest due date should be returned.
     * @return the earliest individual due date, or if none exists the exercise due date.
     */
    public Optional<ZonedDateTime> getEarliestIndividualDueDate(Exercise exercise) {
        if (exercise.getDueDate() == null) {
            // early exit to avoid database call, same result would be produced
            // in Optional.ofNullable(exercise.getDueDate()) below
            return Optional.empty();
        }
        return participationRepository.findEarliestIndividualDueDate(exercise.getId()).or(() -> Optional.ofNullable(exercise.getDueDate()));
    }

    /**
     * Checks if submissions are no longer possible.
     *
     * Checks for exam or course exercise, and if an individual due date is set for the given
     * participation or only a course-wide due date applies.
     * @param participation in a course or exam exercise.
     * @return true, if the due date is in the past and submissions are no longer possible.
     */
    public boolean isAfterDueDate(ParticipationInterface participation) {
        final Exercise exercise = participation.getExercise();
        if (exercise.isExamExercise()) {
            return examDateService.isExerciseWorkingPeriodOver(exercise);
        }
        else {
            final ZonedDateTime now = ZonedDateTime.now();
            return getDueDate(participation).map(now::isAfter).orElse(false);
        }
    }

    /**
     * Checks if the due date for the given participation is in the future.
     * @param participation in a course or exam exercise.
     * @return true, if the due date has not yet passed.
     */
    public boolean isBeforeDueDate(ParticipationInterface participation) {
        return !isAfterDueDate(participation);
    }

    /**
     * Checks if the current time is before the latest possible submission time.
     * If no due date is set, returns true (a due date infinitely far in the future is assumed).
     * @param exercise for which this should be checked.
     * @return true, if the current time is before the due date.
     */
    public boolean isBeforeLatestDueDate(Exercise exercise) {
        final ZonedDateTime now = ZonedDateTime.now();
        return getLatestIndividualDueDate(exercise).map(now::isBefore).orElse(true);
    }

    /**
     * Checks if the current time is after the latest possible submission time.
     * If no due date is set, returns false (a due date infinitely far in the future is assumed).
     * @param exercise for which this should be checked.
     * @return true, if the current time is after the due date.
     */
    public boolean isAfterLatestDueDate(Exercise exercise) {
        return !isBeforeLatestDueDate(exercise);
    }

    /**
     * Checks if due date is before now
     * The due date we use to check depending on whether it is present is individual -> exercise -> none
     * If no due date is set, returns an empty optional
     *
     * @param exercise for which this should be checked.
     * @return Optional of true, if the due date is before the current time.
     */
    public Optional<Boolean> isAfterEarliestDueDate(Exercise exercise) {
        final ZonedDateTime now = ZonedDateTime.now();
        return getEarliestIndividualDueDate(exercise).map(now::isAfter);
    }

    public Optional<Boolean> isBeforeEarliestDueDate(Exercise exercise) {
        return isAfterEarliestDueDate(exercise).map(x -> !x);
    }

    /**
     * Gets either the individual due date for a participation if present or else the exercise due date if present.
     * @param participation of a student in an exercise.
     * @return the individual due date, or the exercise due date, or nothing.
     */
    public Optional<ZonedDateTime> getDueDate(ParticipationInterface participation) {
        final Exercise exercise = participation.getExercise();

        if (participation.getIndividualDueDate() != null) {
            return Optional.of(participation.getIndividualDueDate());
        }
        else if (exercise.getDueDate() != null) {
            return Optional.of(exercise.getDueDate());
        }
        else {
            return Optional.empty();
        }
    }
}
