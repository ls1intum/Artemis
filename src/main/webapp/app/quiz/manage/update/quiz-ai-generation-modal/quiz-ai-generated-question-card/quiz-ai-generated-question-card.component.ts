import { NgClass } from '@angular/common';
import { Component, ViewEncapsulation, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { GeneratedQuestion, GeneratedQuestionType } from 'app/quiz/manage/update/quiz-ai-generation-modal/quiz-ai-generation.types';

@Component({
    selector: 'jhi-quiz-ai-generated-question-card',
    templateUrl: './quiz-ai-generated-question-card.component.html',
    styleUrl: './quiz-ai-generated-question-card.component.scss',
    encapsulation: ViewEncapsulation.None,
    imports: [TranslateDirective, NgClass, HtmlForMarkdownPipe],
})
export class QuizAiGeneratedQuestionCardComponent {
    question = input.required<GeneratedQuestion>();
    questionIndex = input.required<number>();

    getQuestionTypeLabelKey(type: GeneratedQuestionType): string {
        return `artemisApp.quizExercise.aiGeneration.types.${type}`;
    }
}
