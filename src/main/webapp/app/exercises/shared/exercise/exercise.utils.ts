import { SimpleChanges } from '@angular/core';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { Participation } from 'app/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Observable, from, of } from 'rxjs';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { ExerciseServicable } from 'app/exercises/shared/exercise/exercise.service';
import { map, mergeMap, mergeWith, takeUntil } from 'rxjs/operators';
import { ExerciseUpdateWarningComponent } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.component';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

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

    save(exercise: T, isExamMode: boolean, notificationText?: string): Observable<T> {
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

        const popupRefObs = from(this.popupService.checkExerciseBeforeUpdate(exercise, this.backupExercise, isExamMode));

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
 * Determines if the exercise can be started, this is the case if:
 * - It is after the start date or the participant is at least a tutor
 * - In case of a programming exercise it is not before the due date
 * @param exercise the exercise that should be started
 */
export const isStartExerciseAvailable = (exercise: Exercise): boolean => {
    return exercise.type !== ExerciseType.PROGRAMMING || !exercise.dueDate || dayjs().isBefore(exercise.dueDate);
};

/**
 * Determines if the student can resume
 * @param exercise the exercise that should be started
 * @param studentParticipation the optional student participation with possibly an individual due date
 */
export const isResumeExerciseAvailable = (exercise: Exercise, studentParticipation?: StudentParticipation): boolean => {
    if (!studentParticipation?.individualDueDate) {
        return isStartExerciseAvailable(exercise);
    }
    return dayjs().isBefore(studentParticipation.individualDueDate);
};

/**
 * The start practice button should be available for programming and quiz exercises
 * - For quizzes when they are open for practice and the regular work periode is over
 * - For programming exercises when it's after the due date
 */
export const isStartPracticeAvailable = (exercise: Exercise): boolean => {
    switch (exercise.type) {
        case ExerciseType.QUIZ:
            const quizExercise = exercise as QuizExercise;
            return !!(quizExercise.isOpenForPractice && quizExercise.quizEnded);
        case ExerciseType.PROGRAMMING:
            return exercise.dueDate != undefined && dayjs().isAfter(exercise.dueDate) && !exercise.teamMode;
        default:
            return false;
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
