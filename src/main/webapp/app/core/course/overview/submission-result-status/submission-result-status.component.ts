import { Component, Input, OnChanges } from '@angular/core';
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
export class SubmissionResultStatusComponent implements OnChanges {
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
    @Input() exercise: Exercise;
    @Input() studentParticipation?: StudentParticipation;
    @Input() updatingResultClass: string;
    @Input() showBadge = false;
    @Input() showUngradedResults = false;
    @Input() showIcon = true;
    @Input() isInSidebarCard = false;
    @Input() showCompletion = true;
    @Input() short = true;
    @Input() triggerLastGraded = true;
    @Input() showProgressBar = false;

    quizNotStarted: boolean;
    exerciseMissedDueDate: boolean;
    uninitialized: boolean;
    notSubmitted: boolean;
    shouldShowResult: boolean;
    submitted: boolean;

    ngOnChanges() {
        // It's enough to look at the normal due date as students with time extension cannot start after the regular due date
        const afterDueDate = !!this.exercise.dueDate && this.exercise.dueDate.isBefore(dayjs());
        this.exerciseMissedDueDate = afterDueDate && !this.studentParticipation;

        if (this.exercise.type === ExerciseType.QUIZ) {
            const quizExercise = this.exercise as QuizExercise;
            this.uninitialized = ArtemisQuizService.isUninitialized(quizExercise);
            this.quizNotStarted = ArtemisQuizService.notStarted(quizExercise);
            this.submitted = this.studentParticipation?.submissions?.some((submission) => submission.submitted) ?? false;
        } else {
            this.uninitialized = !afterDueDate && !this.studentParticipation;
            this.notSubmitted = afterDueDate && !!this.studentParticipation && !this.studentParticipation.submissions?.length;
        }

        this.setShouldShowResult(afterDueDate);
    }

    private setShouldShowResult(afterDueDate: boolean) {
        if (this.exercise.type === ExerciseType.QUIZ) {
            this.shouldShowResult = !!getAllResultsOfAllSubmissions(this.studentParticipation?.submissions).length;
        } else if (this.exercise.type === ExerciseType.PROGRAMMING) {
            this.shouldShowResult =
                (!!getAllResultsOfAllSubmissions(this.studentParticipation?.submissions).length || !afterDueDate) &&
                !!this.studentParticipation?.initializationState &&
                this.initializationStatesToShowProgrammingResult.includes(this.studentParticipation.initializationState);
        } else {
            this.shouldShowResult = this.studentParticipation?.initializationState === InitializationState.FINISHED;
        }
    }
}
