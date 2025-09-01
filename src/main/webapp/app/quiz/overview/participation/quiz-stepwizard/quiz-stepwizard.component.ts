import { Component, input, output } from '@angular/core';
import { QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { ShortAnswerSubmittedText } from 'app/quiz/shared/entities/short-answer-submitted-text.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgTemplateOutlet } from '@angular/common';

@Component({
    selector: 'jhi-quiz-stepwizard',
    imports: [ArtemisTranslatePipe, NgTemplateOutlet],
    templateUrl: './quiz-stepwizard.component.html',
    styleUrl: './quiz-stepwizard.component.scss',
})
export class QuizStepwizardComponent {
    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    quizExercise = input.required<QuizExercise>();
    mode = input.required<string>();
    selectedAnswerOptions = input<Map<number, AnswerOption[]>>(new Map());
    dragAndDropMappings = input<Map<number, DragAndDropMapping[]>>(new Map());
    shortAnswerSubmittedTexts = input<Map<number, ShortAnswerSubmittedText[]>>(new Map());

    questionSelected = output<number>();
}
