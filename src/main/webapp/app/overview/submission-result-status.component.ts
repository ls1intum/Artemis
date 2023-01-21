import { Component, Input, OnInit } from '@angular/core';
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
export class SubmissionResultStatusComponent implements OnInit {
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
    @Input() short = false;
    @Input() triggerLastGraded = true;

    uninitializedQuiz: boolean;
    quizNotStarted: boolean;
    afterDueDate: boolean;
    uninitialized: boolean;
    notSubmitted: boolean;

    ngOnInit() {
        if (this.exercise.type === ExerciseType.QUIZ) {
            const quizExercise = this.exercise as QuizExercise;
            this.uninitializedQuiz = ArtemisQuizService.isUninitialized(quizExercise);
            this.quizNotStarted = ArtemisQuizService.notStarted(quizExercise);
        }
        this.afterDueDate = !!this.exercise.dueDate && this.exercise.dueDate.isBefore(dayjs());
        this.uninitialized = !this.afterDueDate && !this.exercise.studentParticipations?.length;
        this.notSubmitted = !this.afterDueDate && !!this.exercise.studentParticipations?.length;
    }
}
