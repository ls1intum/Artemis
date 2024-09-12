import { Component, Input, OnChanges } from '@angular/core';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { InitializationState } from 'app/entities/participation/participation.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ArtemisQuizService } from 'app/shared/quiz/quiz.service';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-submission-result-status',
    templateUrl: './submission-result-status.component.html',
})
export class SubmissionResultStatusComponent implements OnChanges {
    private readonly initializationStatesToShowProgrammingResult = [InitializationState.INITIALIZED, InitializationState.INACTIVE, InitializationState.FINISHED];
    readonly ExerciseType = ExerciseType;
    readonly InitializationState = InitializationState;
    readonly dayjs = dayjs;
    @Input() isInsideEditor = false;

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
    @Input() short = true;
    @Input() triggerLastGraded = true;

    quizNotStarted: boolean;
    exerciseMissedDueDate: boolean;
    uninitialized: boolean;
    notSubmitted: boolean;
    shouldShowResult: boolean;

    ngOnChanges() {
        // It's enough to look at the normal due date as students with time extension cannot start after the regular due date
        const afterDueDate = !!this.exercise.dueDate && this.exercise.dueDate.isBefore(dayjs());
        this.exerciseMissedDueDate = afterDueDate && !this.studentParticipation;

        if (this.exercise.type === ExerciseType.QUIZ) {
            const quizExercise = this.exercise as QuizExercise;
            this.uninitialized = ArtemisQuizService.isUninitialized(quizExercise);
            this.quizNotStarted = ArtemisQuizService.notStarted(quizExercise);
        } else {
            this.uninitialized = !afterDueDate && !this.studentParticipation;
            this.notSubmitted = afterDueDate && !!this.studentParticipation && !this.studentParticipation.submissions?.length;
        }

        this.setShouldShowResult(afterDueDate);
    }

    private setShouldShowResult(afterDueDate: boolean) {
        if (this.exercise.type === ExerciseType.QUIZ) {
            this.shouldShowResult = !!this.studentParticipation?.results?.length;
        } else if (this.exercise.type === ExerciseType.PROGRAMMING) {
            this.shouldShowResult =
                (!!this.studentParticipation?.results?.length || !afterDueDate) &&
                !!this.studentParticipation?.initializationState &&
                this.initializationStatesToShowProgrammingResult.includes(this.studentParticipation.initializationState);
        } else {
            this.shouldShowResult = this.studentParticipation?.initializationState === InitializationState.FINISHED;
        }
    }
}
