import { SimpleChanges } from '@angular/core';
import { Exercise, ExerciseType, ParticipationStatus } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { InitializationState, Participation } from 'app/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { from, Observable, of } from 'rxjs';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { ExerciseServicable } from 'app/exercises/shared/exercise/exercise.service';
import { map, mergeMap, mergeWith, takeUntil } from 'rxjs/operators';
import { ExerciseUpdateWarningComponent } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.component';
import { hasResults } from 'app/exercises/shared/participation/participation.utils';
import { AlertService, AlertType } from 'app/core/util/alert.service';

export enum EditType {
    IMPORT,
    CREATE,
    UPDATE,
}

export class SaveExerciseCommand<T extends Exercise> {
    constructor(
        private modalService: NgbModal,
        private popupService: ExerciseUpdateWarningService,
        private exerciseService: ExerciseServicable<T>,
        private backupExercise: T,
        private editType: EditType,
        private alertService: AlertService,
    ) {}

    save(exercise: T, notificationText?: string): Observable<T> {
        const prepareRequestOptions = (): any => {
            switch (this.editType) {
                case EditType.UPDATE:
                    return notificationText ? { notificationText } : {};
                default:
                    return {};
            }
        };

        if (exercise.exampleSolutionPublicationDateWarning) {
            this.alertService.addAlert({
                type: AlertType.WARNING,
                message: 'artemisApp.exercise.exampleSolutionPublicationDateWarning',
            });
        }

        const callServer = ([shouldReevaluate, requestOptions]: [boolean, any?]) => {
            const ex = Exercise.sanitize(exercise);
            switch (this.editType) {
                case EditType.IMPORT:
                    return this.exerciseService.import!(ex);
                case EditType.CREATE:
                    return this.exerciseService.create(ex);
                case EditType.UPDATE:
                    if (shouldReevaluate) {
                        return this.exerciseService.reevaluateAndUpdate(ex, requestOptions);
                    } else {
                        return this.exerciseService.update(ex, requestOptions);
                    }
            }
        };

        let saveObservable = of([false, prepareRequestOptions()]);

        if (exercise.gradingInstructionFeedbackUsed) {
            const popupRefObs = from(this.popupService.checkExerciseBeforeUpdate(exercise, this.backupExercise));

            if (this.modalService.hasOpenModals()) {
                const confirmedCase = popupRefObs.pipe(
                    mergeMap((ref) => (ref.componentInstance as ExerciseUpdateWarningComponent).confirmed.pipe(map(() => [false, prepareRequestOptions()]))),
                );
                const reEvaluatedCase = popupRefObs.pipe(
                    mergeMap((ref) =>
                        (ref.componentInstance as ExerciseUpdateWarningComponent).reEvaluated.pipe(map(() => [true, { deleteFeedback: ref.componentInstance.deleteFeedback }])),
                    ),
                );
                const canceledCase = popupRefObs.pipe(mergeMap((ref) => (ref.componentInstance as ExerciseUpdateWarningComponent).canceled));

                saveObservable = confirmedCase.pipe(mergeWith(reEvaluatedCase), takeUntil(canceledCase));
            }
        }

        return saveObservable.pipe(
            mergeMap(callServer),
            map((res) => res.body! as T),
        );
    }
}

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
 * @param exercise the participation belongs to.
 * @param participation needed to check for an individual due date.
 * @return {boolean} true, if the (individual) due date is in the past.
 */
export const hasExerciseDueDatePassed = (exercise: Exercise, participation?: Participation): boolean => {
    if (exercise.dueDate === undefined) {
        return false;
    }

    const referenceDate = getExerciseDueDate(exercise, participation)!;
    return dayjs(referenceDate).isBefore(dayjs());
};

/**
 * Returns the due date for an exercise.
 *
 * This might either be an individual due date for a participation, the
 * exercise due date itself, or no due date if the exercise has none.
 * @param exercise the participation belongs to.
 * @param participation for which the due date should be found.
 */
export const getExerciseDueDate = (exercise: Exercise, participation?: Participation): dayjs.Dayjs | undefined => {
    if (exercise.dueDate === undefined) {
        return undefined;
    } else {
        return participation?.individualDueDate ?? exercise.dueDate;
    }
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
    // For team exercises check whether the student has been assigned to a team yet
    if (exercise.teamMode && exercise.studentAssignedTeamIdComputed && !exercise.studentAssignedTeamId) {
        return ParticipationStatus.NO_TEAM_ASSIGNED;
    }

    // Evaluate the participation status for quiz exercises.
    if (exercise.type === ExerciseType.QUIZ) {
        return participationStatusForQuizExercise(exercise);
    }

    // Evaluate the participation status for modeling, text and file upload exercises if the exercise has participations.
    if (exercise.type && [ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD].includes(exercise.type) && hasStudentParticipations(exercise)) {
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
    if (!hasStudentParticipations(exercise) || programmingExerciseStates.includes(exercise.studentParticipations![0].initializationState!)) {
        if (exercise.type === ExerciseType.PROGRAMMING && !isStartExerciseAvailable(exercise as ProgrammingExercise)) {
            return ParticipationStatus.EXERCISE_MISSED;
        } else {
            return ParticipationStatus.UNINITIALIZED;
        }
    } else if (exercise.studentParticipations![0].initializationState === InitializationState.INITIALIZED) {
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
        exercise.dueDate == undefined ||
        dayjs() <= exercise.dueDate! ||
        (exercise.buildAndTestStudentSubmissionsAfterDueDate == undefined &&
            exercise.assessmentType === AssessmentType.AUTOMATIC &&
            !exercise.allowComplaintsForAutomaticAssessments)
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
    if (quizExercise.quizEnded) {
        if (hasStudentParticipations(exercise) && hasResults(exercise.studentParticipations![0])) {
            return ParticipationStatus.QUIZ_FINISHED;
        }
        return ParticipationStatus.QUIZ_NOT_PARTICIPATED;
    } else if (hasStudentParticipations(exercise)) {
        if (exercise.studentParticipations![0].initializationState === InitializationState.INITIALIZED) {
            return ParticipationStatus.QUIZ_ACTIVE;
        } else if (exercise.studentParticipations![0].initializationState === InitializationState.FINISHED) {
            return ParticipationStatus.QUIZ_SUBMITTED;
        }
    } else if (quizExercise?.quizBatches?.some((batch) => batch.started)) {
        return ParticipationStatus.QUIZ_UNINITIALIZED;
    }
    return ParticipationStatus.QUIZ_NOT_STARTED;
};

/**
 * Handles the evaluation of participation status for modeling, text and file upload exercises if the exercise has participations.
 *
 * @param exercise
 * @return {ParticipationStatus}
 */
const participationStatusForModelingTextFileUploadExercise = (exercise: Exercise): ParticipationStatus => {
    const participation = exercise.studentParticipations![0];

    // An exercise is active (EXERCISE_ACTIVE) if it is initialized and has not passed its due date.
    // A more detailed evaluation of active exercises takes place in the result component.
    // An exercise was missed (EXERCISE_MISSED) if it is initialized and has passed its due date (due date lies in the past).
    if (participation.initializationState === InitializationState.INITIALIZED) {
        return hasExerciseDueDatePassed(exercise, participation) ? ParticipationStatus.EXERCISE_MISSED : ParticipationStatus.EXERCISE_ACTIVE;
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
    const progEx = exercise as ProgrammingExercise;
    const relevantDueDate = progEx.buildAndTestStudentSubmissionsAfterDueDate ?? progEx.dueDate;
    return (
        (!!progEx.isAtLeastTutor || !!progEx.isAtLeastEditor || !!progEx.isAtLeastInstructor) &&
        progEx.assessmentType === AssessmentType.SEMI_AUTOMATIC &&
        (!relevantDueDate || dayjs(relevantDueDate).isBefore(dayjs()))
    );
};

/**
 * Gets the positive and capped to maximum points which is fixed at two decimal numbers
 *
 * @param totalScore the calculated score of a student
 * @param maxPoints the maximal points (including bonus points) of an exercise
 */
export const getPositiveAndCappedTotalScore = (totalScore: number, maxPoints: number): number => {
    // Do not allow negative score
    if (totalScore < 0) {
        totalScore = 0;
    }
    // Cap totalScore to maxPoints
    if (totalScore > maxPoints) {
        totalScore = maxPoints;
    }

    return +totalScore.toFixed(2);
};
