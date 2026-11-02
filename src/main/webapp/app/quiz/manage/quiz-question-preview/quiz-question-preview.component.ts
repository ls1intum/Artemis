import { Component, computed, input, linkedSignal } from '@angular/core';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { MultipleChoiceQuestionComponent } from 'app/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { DragAndDropQuestionComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { ShortAnswerQuestionComponent } from 'app/quiz/shared/questions/short-answer-question/short-answer-question.component';

/**
 * Rendering of one quiz question as a student would see it, starting without any answer.
 * The question can be tried out like the preview of the quiz editor, but nothing is stored and the question itself is never changed.
 * Used by the import dialogs so that an instructor can look at a question or a whole quiz before importing it.
 */
@Component({
    selector: 'jhi-quiz-question-preview',
    templateUrl: './quiz-question-preview.component.html',
    imports: [MultipleChoiceQuestionComponent, DragAndDropQuestionComponent, ShortAnswerQuestionComponent],
})
export class QuizQuestionPreviewComponent {
    protected readonly QuizQuestionType = QuizQuestionType;

    readonly question = input.required<QuizQuestion>();
    /** The one-based position shown in front of the question title. */
    readonly questionIndex = input<number>(1);

    protected readonly multipleChoiceQuestion = computed(() => this.question() as MultipleChoiceQuestion);

    /**
     * The items that have been dropped while trying out a drag and drop question. The question component only reports a changed mapping, so they have to be kept
     * here to stay where they were dropped. They start empty for every question, are never stored, and do not change the question.
     */
    protected readonly dragAndDropMappings = linkedSignal<QuizQuestion, DragAndDropMapping[]>({ source: this.question, computation: () => [] });
}
