import { Component, EventEmitter, Input, Output, ViewEncapsulation } from '@angular/core';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { CdkDrag, CdkDragDrop, CdkDropList, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';

@Component({
    selector: 'jhi-quiz-pool-mapping-question-list',
    templateUrl: './quiz-pool-mapping-question-list.component.html',
    styleUrls: ['./quiz-pool-mapping-question-list.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [CdkDropList, CdkDrag],
})
export class QuizPoolMappingQuestionListComponent {
    @Input() quizQuestions: Array<QuizQuestion>;
    @Input() disabled = false;

    @Output() onQuizQuestionDropped: EventEmitter<QuizQuestion> = new EventEmitter<QuizQuestion>();

    MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    /**
     * If the quiz question is dropped to the same group, move the index. Otherwise, move the quiz question to the new group.
     * Then emit onQuizQuestionDropped of the newly dropped QuizQuestion.
     *
     * @param event the onDropListDropped event
     */
    handleOnDropQuestion(event: CdkDragDrop<QuizQuestion[]>) {
        if (event.previousContainer === event.container) {
            moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
        } else {
            transferArrayItem(event.previousContainer.data, event.container.data, event.previousIndex, event.currentIndex);
        }
        const quizQuestion = event.container.data[event.currentIndex];
        this.onQuizQuestionDropped.emit(quizQuestion);
    }
}
