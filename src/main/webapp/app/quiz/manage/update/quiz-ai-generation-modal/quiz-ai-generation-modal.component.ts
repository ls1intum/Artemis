import { NgClass } from '@angular/common';
import { Component, ViewEncapsulation, computed, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { SliderModule } from 'primeng/slider';
import { TextareaModule } from 'primeng/textarea';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent, FaStackComponent, FaStackItemSizeDirective } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { QuizAiGeneratedQuestionCardComponent } from 'app/quiz/manage/update/quiz-ai-generation-modal/quiz-ai-generated-question-card/quiz-ai-generated-question-card.component';
import { GeneratedQuestion, GeneratedQuestionTemplate, GeneratedQuestionType, GenerationLanguage } from 'app/quiz/manage/update/quiz-ai-generation-modal/quiz-ai-generation.types';

@Component({
    selector: 'jhi-quiz-ai-generation-modal',
    templateUrl: './quiz-ai-generation-modal.component.html',
    styleUrl: './quiz-ai-generation-modal.component.scss',
    encapsulation: ViewEncapsulation.None,
    imports: [
        DialogModule,
        ButtonModule,
        InputTextModule,
        InputNumberModule,
        SliderModule,
        TextareaModule,
        FormsModule,
        TranslateDirective,
        NgClass,
        FaStackComponent,
        FaIconComponent,
        FaStackItemSizeDirective,
        NgbTooltip,
        ArtemisTranslatePipe,
        QuizAiGeneratedQuestionCardComponent,
    ],
})
export class QuizAiGenerationModalComponent {
    protected readonly faQuestionCircle = faQuestionCircle;
    visible = model.required<boolean>();
    topic = signal('');
    optionalPrompt = signal('');
    numberOfQuestions = signal(3);
    difficulty = signal(50);
    language = signal<GenerationLanguage>('en');
    selectedQuestionTypes = signal<GeneratedQuestionType[]>(['single-choice']);
    generatedQuestions = signal<GeneratedQuestion[]>([]);

    readonly questionTypes: GeneratedQuestionType[] = ['single-choice', 'multiple-choice', 'true-false'];
    readonly languages: GenerationLanguage[] = ['en', 'de'];
    readonly hasGeneratedQuestions = computed(() => this.generatedQuestions().length > 0);

    close(): void {
        this.visible.set(false);
    }

    toggleQuestionType(type: GeneratedQuestionType): void {
        const currentlySelectedTypes = this.selectedQuestionTypes();
        if (currentlySelectedTypes.includes(type)) {
            this.selectedQuestionTypes.set(currentlySelectedTypes.filter((selectedType) => selectedType !== type));
            return;
        }
        this.selectedQuestionTypes.set([...currentlySelectedTypes, type]);
    }

    selectLanguage(language: GenerationLanguage): void {
        this.language.set(language);
    }

    isLanguageSelected(language: GenerationLanguage): boolean {
        return this.language() === language;
    }

    isQuestionTypeSelected(type: GeneratedQuestionType): boolean {
        return this.selectedQuestionTypes().includes(type);
    }

    generateQuestions(): void {
        const selectedTypes = this.selectedQuestionTypes();
        const questionCount = Math.max(1, this.numberOfQuestions() ?? 1);
        const templates = this.getTemplatesForSelectedTypes(selectedTypes);
        const generated = Array.from({ length: questionCount }, (_, index) => {
            const template = templates[index % templates.length];
            return {
                id: `${template.key}-${index}`,
                type: template.type,
                questionKey: template.questionKey,
                options: template.options,
            };
        });
        this.generatedQuestions.set(generated);
    }

    getQuestionTypeLabelKey(type: GeneratedQuestionType): string {
        return `artemisApp.quizExercise.aiGeneration.types.${type}`;
    }

    getLanguageLabelKey(language: GenerationLanguage): string {
        return `artemisApp.quizExercise.aiGeneration.languages.${language}`;
    }

    getResultCountTextKey(): string {
        return this.generatedQuestions().length === 1
            ? 'artemisApp.quizExercise.aiGeneration.preview.oneResultGenerated'
            : 'artemisApp.quizExercise.aiGeneration.preview.multipleResultsGenerated';
    }

    getResultCountTranslateValues() {
        return { count: this.generatedQuestions().length };
    }

    private getTemplatesForSelectedTypes(selectedTypes: GeneratedQuestionType[]): GeneratedQuestionTemplate[] {
        const templates: GeneratedQuestionTemplate[] = [
            {
                key: 'single-choice',
                type: 'single-choice',
                questionKey: `artemisApp.quizExercise.aiGeneration.mock.${this.language()}.singleChoice.question`,
                options: [
                    { key: `artemisApp.quizExercise.aiGeneration.mock.${this.language()}.singleChoice.optionA`, correct: true },
                    { key: `artemisApp.quizExercise.aiGeneration.mock.${this.language()}.singleChoice.optionB`, correct: false },
                    { key: `artemisApp.quizExercise.aiGeneration.mock.${this.language()}.singleChoice.optionC`, correct: false },
                ],
            },
            {
                key: 'multiple-choice',
                type: 'multiple-choice',
                questionKey: `artemisApp.quizExercise.aiGeneration.mock.${this.language()}.multipleChoice.question`,
                options: [
                    { key: `artemisApp.quizExercise.aiGeneration.mock.${this.language()}.multipleChoice.optionA`, correct: true },
                    { key: `artemisApp.quizExercise.aiGeneration.mock.${this.language()}.multipleChoice.optionB`, correct: true },
                    { key: `artemisApp.quizExercise.aiGeneration.mock.${this.language()}.multipleChoice.optionC`, correct: false },
                ],
            },
            {
                key: 'true-false',
                type: 'true-false',
                questionKey: `artemisApp.quizExercise.aiGeneration.mock.${this.language()}.trueFalse.question`,
                options: [
                    { key: `artemisApp.quizExercise.aiGeneration.mock.${this.language()}.trueFalse.optionA`, correct: false },
                    { key: `artemisApp.quizExercise.aiGeneration.mock.${this.language()}.trueFalse.optionB`, correct: true },
                ],
            },
        ];

        const filteredTemplates = templates.filter((template) => selectedTypes.includes(template.type));
        return filteredTemplates.length > 0 ? filteredTemplates : templates;
    }
}
