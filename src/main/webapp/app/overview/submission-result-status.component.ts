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
    @Input() short = true;
    @Input() triggerLastGraded = true;

    quizNotStarted: boolean;
    exerciseMissedDeadline: boolean;
    uninitialized: boolean;
    notSubmitted: boolean;

    ngOnChanges() {
        const afterDueDate = !!this.exercise.dueDate && this.exercise.dueDate.isBefore(dayjs());
        this.exerciseMissedDeadline = afterDueDate && !this.studentParticipation;

        if (this.exercise.type === ExerciseType.QUIZ) {
            const quizExercise = this.exercise as QuizExercise;
            this.uninitialized = ArtemisQuizService.isUninitialized(quizExercise);
            this.quizNotStarted = ArtemisQuizService.notStarted(quizExercise);
        } else {
            this.uninitialized = !afterDueDate && !this.studentParticipation;
            this.notSubmitted = !afterDueDate && !!this.studentParticipation && !this.studentParticipation.submissions?.length;
        }
    }
}
