import { SimpleChanges } from '@angular/core';
import { Exercise, ExerciseType, ParticipationStatus } from 'app/entities/exercise/exercise.model';
import { hasResults, InitializationState } from 'app/entities/participation';
import { QuizExercise } from 'app/entities/quiz-exercise';
import * as moment from 'moment';
import { now } from 'moment';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { AssessmentType } from 'app/entities/assessment-type';

export const hasExerciseChanged = (changes: SimpleChanges) => {
    return changes.exercise && changes.exercise.currentValue && (!changes.exercise.previousValue || changes.exercise.previousValue.id !== changes.exercise.currentValue.id);
};

export const problemStatementHasChanged = (changes: SimpleChanges) => {
    return (
        changes.exercise &&
        changes.exercise.currentValue &&
        (!changes.exercise.previousValue || changes.exercise.previousValue.problemStatement !== changes.exercise.currentValue.problemStatement)
    );
};

/**
 * Checks if the due date of a given exercise lies in the past. If there is no due date it evaluates to false.
 *
 * @param exercise
 * @return {boolean}
 */
export const hasExerciseDueDatePassed = (exercise: Exercise): boolean => {
    if (exercise.dueDate == null) {
        return false;
    }
    return moment(exercise.dueDate).isBefore();
};

/**
 * Checks if the given exercise has student participations.
 *
 * @param exercise
 * @return {boolean}
 */
export const hasStudentParticipations = (exercise: Exercise) => {
    return exercise.studentParticipations && exercise.studentParticipations.length > 0;
};

/**
 * Handles the evaluation of participation status.
 *
 * @param exercise
 * @return {ParticipationStatus}
 */
export const participationStatus = (exercise: Exercise): ParticipationStatus => {
    // Evaluate the participation status for quiz exercises.
    if (exercise.type === ExerciseType.QUIZ) {
        return participationStatusForQuizExercise(exercise);
    }

    // Evaluate the participation status for modeling, text and file upload exercises if the exercise has participations.
    if ([ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD].includes(exercise.type) && hasStudentParticipations(exercise)) {
        return participationStatusForModelingTextFileUploadExercise(exercise);
    }

    const programmingExerciseStates = [
        InitializationState.UNINITIALIZED,
        InitializationState.REPO_COPIED,
        InitializationState.REPO_CONFIGURED,
        InitializationState.BUILD_PLAN_COPIED,
        InitializationState.BUILD_PLAN_CONFIGURED,
    ];

    // The following evaluations are relevant for programming exercises in general and for modeling, text and file upload exercises that don't have participations.
    if (!hasStudentParticipations(exercise) || programmingExerciseStates.includes(exercise.studentParticipations[0].initializationState)) {
        if (exercise.type === ExerciseType.PROGRAMMING && !isStartExerciseAvailable(exercise as ProgrammingExercise)) {
            return ParticipationStatus.EXERCISE_MISSED;
        } else {
            return ParticipationStatus.UNINITIALIZED;
        }
    } else if (exercise.studentParticipations[0].initializationState === InitializationState.INITIALIZED) {
        return ParticipationStatus.INITIALIZED;
    }
    return ParticipationStatus.INACTIVE;
};

/**
 * The start exercise button should be available for programming exercises when
 * - there is no due date
 * - now is before the due date
 * - test run after due date is deactivated and manual grading is deactivated
 */
export const isStartExerciseAvailable = (exercise: ProgrammingExercise): boolean => {
    return (
        exercise.dueDate == null ||
        moment() <= exercise.dueDate! ||
        (exercise.buildAndTestStudentSubmissionsAfterDueDate == null && exercise.assessmentType === AssessmentType.AUTOMATIC)
    );
};

/**
 * Handles the evaluation of participation status for quiz exercises.
 *
 * @param exercise
 * @return {ParticipationStatus}
 */
const participationStatusForQuizExercise = (exercise: Exercise): ParticipationStatus => {
    const quizExercise = exercise as QuizExercise;
    if ((!quizExercise.isPlannedToStart || moment(quizExercise.releaseDate!).isAfter(moment())) && quizExercise.visibleToStudents) {
        return ParticipationStatus.QUIZ_NOT_STARTED;
    } else if (!hasStudentParticipations(exercise) && (!quizExercise.isPlannedToStart || moment(quizExercise.dueDate!).isAfter(moment())) && quizExercise.visibleToStudents) {
        return ParticipationStatus.QUIZ_UNINITIALIZED;
    } else if (!hasStudentParticipations(exercise)) {
        return ParticipationStatus.QUIZ_NOT_PARTICIPATED;
    } else if (exercise.studentParticipations[0].initializationState === InitializationState.INITIALIZED && moment(exercise.dueDate!).isAfter(moment())) {
        return ParticipationStatus.QUIZ_ACTIVE;
    } else if (exercise.studentParticipations[0].initializationState === InitializationState.FINISHED && moment(exercise.dueDate!).isAfter(moment())) {
        return ParticipationStatus.QUIZ_SUBMITTED;
    } else {
        return !hasResults(exercise.studentParticipations[0]) ? ParticipationStatus.QUIZ_NOT_PARTICIPATED : ParticipationStatus.QUIZ_FINISHED;
    }
};

/**
 * Handles the evaluation of participation status for modeling, text and file upload exercises if the exercise has participations.
 *
 * @param exercise
 * @return {ParticipationStatus}
 */
const participationStatusForModelingTextFileUploadExercise = (exercise: Exercise): ParticipationStatus => {
    const participation = exercise.studentParticipations[0];

    // An exercise is active (EXERCISE_ACTIVE) if it is initialized and has not passed its due date. The more detailed evaluation of active exercises takes place in the result component.
    // An exercise was missed (EXERCISE_MISSED) if it is initialized and has passed its due date (due date lies in the past).
    if (participation.initializationState === InitializationState.INITIALIZED) {
        return hasExerciseDueDatePassed(exercise) ? ParticipationStatus.EXERCISE_MISSED : ParticipationStatus.EXERCISE_ACTIVE;
    } else if (participation.initializationState === InitializationState.FINISHED) {
        // An exercise was submitted (EXERCISE_SUBMITTED) if the corresponding InitializationState is set to FINISHED
        return ParticipationStatus.EXERCISE_SUBMITTED;
    } else {
        return ParticipationStatus.UNINITIALIZED;
    }
};

/**
 * Checks whether the given exercise is eligible for receiving manual results.
 * This is the case if the user is at least a tutor and the exercise itself is a programming
 * exercise for which manual reviews have been enabled. The due date also has to be in the past.
 *
 * @param exercise
 */
export const areManualResultsAllowed = (exercise: Exercise) => {
    if (exercise.type !== ExerciseType.PROGRAMMING) {
        return false;
    }
    // Only allow new results if manual reviews are activated and the due date/after due date has passed
    const exc = exercise as ProgrammingExercise;
    const relevantDueDate = exc.buildAndTestStudentSubmissionsAfterDueDate ? exc.buildAndTestStudentSubmissionsAfterDueDate : exc.dueDate;
    return exc.isAtLeastTutor && exc.assessmentType === AssessmentType.SEMI_AUTOMATIC && (!relevantDueDate || moment(relevantDueDate).isBefore(now()));
};
