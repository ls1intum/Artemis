import { Component, input } from '@angular/core';
import { TumAetUiTagComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { GeneratedQuestion, GeneratedQuestionType } from 'app/hyperion/quiz/quiz-ai-generation-modal/quiz-ai-generation.types';

@Component({
    selector: 'jhi-quiz-ai-generated-question-card',
    templateUrl: './quiz-ai-generated-question-card.component.html',
    imports: [TranslateDirective, TumAetUiTagComponent, MarkdownDirective],
})
export class QuizAiGeneratedQuestionCardComponent {
    question = input.required<GeneratedQuestion>();
    questionIndex = input.required<number>();
    /** Shows the explanation of the question and of each option. */
    showExplanations = input(false);

    getQuestionTypeLabelKey(type: GeneratedQuestionType): string {
        return `artemisApp.quizExercise.aiGeneration.types.${type}`;
    }
}
