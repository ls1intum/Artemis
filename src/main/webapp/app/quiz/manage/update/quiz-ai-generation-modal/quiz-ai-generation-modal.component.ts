import { NgClass } from '@angular/common';
import { Component, ViewEncapsulation, computed, inject, input, model, output, signal } from '@angular/core';
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
import { AlertService } from 'app/shared/service/alert.service';
import { QuizAiGeneratedQuestionCardComponent } from 'app/quiz/manage/update/quiz-ai-generation-modal/quiz-ai-generated-question-card/quiz-ai-generated-question-card.component';
import { GeneratedQuestion, GeneratedQuestionType, GenerationLanguage } from 'app/quiz/manage/update/quiz-ai-generation-modal/quiz-ai-generation.types';
import { QuizAiGenerationService } from 'app/quiz/manage/update/quiz-ai-generation-modal/quiz-ai-generation.service';
import { finalize } from 'rxjs/operators';
import { QuizQuestionGenerationRequest } from 'app/openapi/model/quizQuestionGenerationRequest';

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
    private alertService = inject(AlertService);
    private quizAiGenerationService = inject(QuizAiGenerationService);

    protected readonly faQuestionCircle = faQuestionCircle;
    visible = model.required<boolean>();
    courseId = input<number>();
    addQuestions = output<GeneratedQuestion[]>();
    topic = signal('');
    optionalPrompt = signal('');
    numberOfQuestions = signal(3);
    difficulty = signal(50);
    language = signal<GenerationLanguage>('en');
    selectedQuestionTypes = signal<GeneratedQuestionType[]>(['single-choice']);
    generatedQuestions = signal<GeneratedQuestion[]>([]);
    isGenerating = signal(false);

    readonly questionTypes: GeneratedQuestionType[] = ['single-choice', 'multiple-choice', 'true-false'];
    readonly languages: GenerationLanguage[] = ['en', 'de'];
    readonly hasGeneratedQuestions = computed(() => this.generatedQuestions().length > 0);
    readonly canGenerate = computed(() => !!this.courseId() && !!this.topic().trim() && this.selectedQuestionTypes().length > 0);

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
        const courseId = this.courseId();
        if (!courseId || !this.canGenerate()) {
            this.alertService.error('artemisApp.quizExercise.aiGeneration.errors.invalidConfiguration');
            return;
        }

        const request: QuizQuestionGenerationRequest = {
            topic: this.topic().trim(),
            optionalPrompt: this.optionalPrompt().trim() || undefined,
            language: this.language(),
            questionTypes: this.selectedQuestionTypes(),
            numberOfQuestions: Math.max(1, this.numberOfQuestions() ?? 1),
            difficulty: Math.max(0, Math.min(100, this.difficulty() ?? 50)),
        };

        this.isGenerating.set(true);
        this.quizAiGenerationService
            .generateQuizQuestions(courseId, request)
            .pipe(finalize(() => this.isGenerating.set(false)))
            .subscribe({
                next: (generatedQuestions) => {
                    this.generatedQuestions.set(generatedQuestions);
                },
                error: () => {
                    this.alertService.error('artemisApp.quizExercise.aiGeneration.errors.generationFailed');
                },
            });
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

    addGeneratedQuestionsToQuiz(): void {
        const questions = this.generatedQuestions();
        if (!questions.length) {
            return;
        }
        this.addQuestions.emit(questions);
        this.close();
    }
}
