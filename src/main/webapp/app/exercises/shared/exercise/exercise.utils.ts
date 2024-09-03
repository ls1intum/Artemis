import { SimpleChanges } from '@angular/core';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { InitializationState, Participation } from 'app/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Observable, from, of } from 'rxjs';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { ExerciseServicable } from 'app/exercises/shared/exercise/exercise.service';
import { map, mergeMap, mergeWith, takeUntil } from 'rxjs/operators';
import { ExerciseUpdateWarningComponent } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.component';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { StudentParticipation, isPracticeMode } from 'app/entities/participation/student-participation.model';

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
export function getExerciseDueDate(exercise: Exercise, participation?: Participation): dayjs.Dayjs | undefined {
    if (exercise.dueDate === undefined) {
        return undefined;
    } else {
        return participation?.individualDueDate ?? exercise.dueDate;
    }
}

/**
 * Determines if the exercise can be started, this is the case if:
 * - In case of a programming exercise it is not before the due date
 * - There is no participation or in case of a programming exercise the setup is not yet finished
 * @param exercise the exercise that should be started
 * @param participation the potentially existing participation
 */
export const isStartExerciseAvailable = (exercise: Exercise, participation?: StudentParticipation): boolean => {
    const isProgrammingExercise = exercise.type === ExerciseType.PROGRAMMING;
    const validDueDate = !isProgrammingExercise || !exercise.dueDate || dayjs().isBefore(exercise.dueDate);

    return validDueDate && (!participation || (isProgrammingExercise && programmingSetupNotFinished(participation)));
};

/**
 * Determines if the student can resume a participation
 * @param exercise the exercise that should be started
 * @param participation the optional student participation with possibly an individual due date
 */
export const isResumeExerciseAvailable = (exercise: Exercise, participation?: StudentParticipation): boolean => {
    const dueDate = participation?.individualDueDate ?? exercise.dueDate;
    // A normal participation may only be resumed before the due date, a testrun only afterwards
    return (!dueDate || dayjs().isBefore(dueDate)) === !isPracticeMode(participation);
};

/**
 * The start practice button should be available for programming and quiz exercises
 * - For quizzes when they are open for practice and the regular work period is over
 * - For programming exercises when it's after the due date
 * @param exercise the exercise that the student wants to practice
 * @param participation the potentially existing participation
 */
export const isStartPracticeAvailable = (exercise: Exercise, participation?: StudentParticipation): boolean => {
    switch (exercise.type) {
        case ExerciseType.QUIZ:
            const quizExercise = exercise as QuizExercise;
            return !!(quizExercise.isOpenForPractice && quizExercise.quizEnded);
        case ExerciseType.PROGRAMMING:
            return exercise.dueDate != undefined && dayjs().isAfter(exercise.dueDate) && !exercise.teamMode && (!participation || programmingSetupNotFinished(participation));
        default:
            return false;
    }
};

/**
 * Checks whether the given exercise is eligible for receiving manual results.
 * If it is a programming exercise, the due date also has to be in the past.
 *
 * @param exercise
 */
export const areManualResultsAllowed = (exercise: Exercise): boolean => {
    if (exercise.type === ExerciseType.QUIZ) {
        return false;
    } else if (exercise.type !== ExerciseType.PROGRAMMING) {
        return !exercise.dueDate || dayjs().isAfter(exercise.dueDate);
    } else {
        const relevantDueDate = (exercise as ProgrammingExercise).buildAndTestStudentSubmissionsAfterDueDate ?? exercise.dueDate;
        return exercise.assessmentType !== AssessmentType.AUTOMATIC && (!relevantDueDate || dayjs().isAfter(relevantDueDate));
    }
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

export const getTotalMaxPoints = (exercise?: Exercise): number => {
    return (exercise?.maxPoints ?? 0) + (exercise?.bonusPoints ?? 0);
};

const programmingSetupNotFinished = (participation: StudentParticipation): boolean => {
    return (
        !!participation.initializationState &&
        [InitializationState.UNINITIALIZED, InitializationState.BUILD_PLAN_CONFIGURED, InitializationState.REPO_CONFIGURED, InitializationState.REPO_COPIED].includes(
            participation.initializationState,
        )
    );
};
