import { Component, computed, input } from '@angular/core';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import dayjs from 'dayjs/esm';

import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/student/programming-exercise-student-trigger-build-button.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisQuizService } from 'app/quiz/shared/service/quiz.service';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';

@Component({
    selector: 'jhi-submission-result-status',
    templateUrl: './submission-result-status.component.html',
    imports: [UpdatingResultComponent, TranslateDirective, ProgrammingExerciseStudentTriggerBuildButtonComponent],
})
export class SubmissionResultStatusComponent {
    private readonly initializationStatesToShowProgrammingResult = [InitializationState.INITIALIZED, InitializationState.INACTIVE, InitializationState.FINISHED];
    readonly ExerciseType = ExerciseType;
    readonly InitializationState = InitializationState;
    readonly dayjs = dayjs;

    /**
     * @property exercise Exercise to which the submission's participation belongs
     * @property studentParticipation Participation to which the submission belongs
     * @property updatingResultClass Class(es) that will be applied to the updating-result component
     * @property showBadge Flag whether a colored badge (saying e.g. "Graded") should be shown
     * @property showUngradedResults Flag whether ungraded results should also be shown
     * @property short Flag whether the short version of the result text should be used
     */
    readonly exercise = input.required<Exercise>();
    readonly studentParticipation = input<StudentParticipation>();
    readonly updatingResultClass = input<string>('');
    readonly showBadge = input(false);
    readonly showUngradedResults = input(false);
    readonly showIcon = input(true);
    readonly isInSidebarCard = input(false);
    readonly showCompletion = input(true);
    readonly short = input(true);
    readonly triggerLastGraded = input(true);
    readonly showProgressBar = input(false);

    // Computed signal for whether due date has passed
    private readonly afterDueDate = computed(() => {
        const exercise = this.exercise();
        return !!exercise?.dueDate && exercise.dueDate.isBefore(dayjs());
    });

    readonly quizNotStarted = computed(() => {
        const exercise = this.exercise();
        if (exercise?.type !== ExerciseType.QUIZ) {
            return false;
        }
        return ArtemisQuizService.notStarted(exercise as QuizExercise);
    });

    readonly exerciseMissedDueDate = computed(() => {
        return this.afterDueDate() && !this.studentParticipation();
    });

    readonly uninitialized = computed(() => {
        const exercise = this.exercise();
        const studentParticipation = this.studentParticipation();

        if (exercise?.type === ExerciseType.QUIZ) {
            return ArtemisQuizService.isUninitialized(exercise as QuizExercise);
        }
        return !this.afterDueDate() && !studentParticipation;
    });

    readonly notSubmitted = computed(() => {
        const exercise = this.exercise();
        const studentParticipation = this.studentParticipation();

        if (exercise?.type === ExerciseType.QUIZ) {
            return false;
        }
        return this.afterDueDate() && !!studentParticipation && !studentParticipation.submissions?.length;
    });

    readonly submitted = computed(() => {
        const exercise = this.exercise();
        const studentParticipation = this.studentParticipation();

        if (exercise?.type !== ExerciseType.QUIZ) {
            return false;
        }
        return studentParticipation?.submissions?.some((submission) => submission.submitted) ?? false;
    });

    readonly shouldShowResult = computed(() => {
        const exercise = this.exercise();
        const studentParticipation = this.studentParticipation();

        if (exercise?.type === ExerciseType.QUIZ) {
            return !!getAllResultsOfAllSubmissions(studentParticipation?.submissions).length;
        } else if (exercise?.type === ExerciseType.PROGRAMMING) {
            return (
                (!!getAllResultsOfAllSubmissions(studentParticipation?.submissions).length || !this.afterDueDate()) &&
                !!studentParticipation?.initializationState &&
                this.initializationStatesToShowProgrammingResult.includes(studentParticipation.initializationState)
            );
        } else {
            return studentParticipation?.initializationState === InitializationState.FINISHED;
        }
    });
}
