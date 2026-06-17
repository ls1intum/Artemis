import { NgClass } from '@angular/common';
import { Component, OnDestroy, ViewEncapsulation, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { SliderModule } from 'primeng/slider';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { MultiSelectModule } from 'primeng/multiselect';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent, FaStackComponent, FaStackItemSizeDirective } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { QuizAiGeneratedQuestionCardComponent } from 'app/quiz/manage/update/quiz-ai-generation-modal/quiz-ai-generated-question-card/quiz-ai-generated-question-card.component';
import { GeneratedQuestion, GeneratedQuestionType, GenerationLanguage } from 'app/quiz/manage/update/quiz-ai-generation-modal/quiz-ai-generation.types';
import { QuizAiGenerationService } from 'app/quiz/manage/update/quiz-ai-generation-modal/quiz-ai-generation.service';
import { finalize } from 'rxjs/operators';
import { Subscription, from } from 'rxjs';
import { QuizQuestionGenerationRequest } from 'app/openapi/model/quizQuestionGenerationRequest';
import { CourseCompetency } from 'app/atlas/shared/entities/competency.model';
import { CourseCompetencyApiService } from 'app/atlas/shared/services/course-competency-api.service';

type GenerationMode = 'free-topic' | 'competency-graph';

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
        TooltipModule,
        FormsModule,
        TranslateDirective,
        NgClass,
        FaStackComponent,
        FaIconComponent,
        FaStackItemSizeDirective,
        ArtemisTranslatePipe,
        QuizAiGeneratedQuestionCardComponent,
        MultiSelectModule,
    ],
})
export class QuizAiGenerationModalComponent implements OnDestroy {
    private alertService = inject(AlertService);
    private quizAiGenerationService = inject(QuizAiGenerationService);
    private courseCompetencyApiService = inject(CourseCompetencyApiService);

    protected readonly faQuestionCircle = faQuestionCircle;
    visible = model.required<boolean>();
    courseId = input<number>();
    addQuestions = output<GeneratedQuestion[]>();

    // shared
    mode = signal<GenerationMode>('free-topic');
    optionalPrompt = signal('');
    numberOfQuestions = signal(3);
    language = signal<GenerationLanguage>('en');
    selectedQuestionTypes = signal<GeneratedQuestionType[]>(['single-choice']);
    generatedQuestions = signal<GeneratedQuestion[]>([]);
    isGenerating = signal(false);
    loadingPhraseIndex = signal(0);
    loadingDotsCount = signal(0);

    // free-topic mode
    topic = signal('');
    difficulty = signal(50);

    // competency-graph mode
    courseCompetencies = signal<CourseCompetency[]>([]);
    selectedCompetencies = signal<CourseCompetency[]>([]);
    isLoadingCompetencies = signal(false);
    /** Tracks the courseId for which a competency load was last attempted (success or failure). */
    private competenciesLoadedForCourseId = signal<number | null>(null);

    readonly questionTypes: GeneratedQuestionType[] = ['single-choice', 'multiple-choice', 'true-false'];
    readonly languages: GenerationLanguage[] = ['en', 'de'];
    readonly loadingPhraseKeys = [
        'artemisApp.quizExercise.aiGeneration.actions.loading.thinking',
        'artemisApp.quizExercise.aiGeneration.actions.loading.analyzing',
        'artemisApp.quizExercise.aiGeneration.actions.loading.generating',
        'artemisApp.quizExercise.aiGeneration.actions.loading.refining',
    ] as const;

    readonly isCompetencyMode = computed(() => this.mode() === 'competency-graph');
    readonly hasCompetencies = computed(() => this.courseCompetencies().length > 0);
    readonly hasGeneratedQuestions = computed(() => this.generatedQuestions().length > 0);
    readonly canGenerate = computed(() => {
        if (!this.courseId() || this.selectedQuestionTypes().length === 0) {
            return false;
        }
        if (!this.isCompetencyMode()) {
            return !!this.topic().trim();
        }
        return this.selectedCompetencies().length > 0;
    });

    private loadingPhraseIntervalId?: ReturnType<typeof setInterval>;
    private loadingDotsIntervalId?: ReturnType<typeof setInterval>;
    private generationSubscription?: Subscription;
    private competencySubscription?: Subscription;

    constructor() {
        // Load competencies once per open/course cycle; guards against infinite retries on empty
        // results or request failures by keying on competenciesLoadedForCourseId, not list length.
        effect(() => {
            const courseId = this.courseId();
            if (this.visible() && courseId && this.competenciesLoadedForCourseId() !== courseId && !this.isLoadingCompetencies()) {
                this.loadCompetencies(courseId);
            }
        });
        // Reset the load-guard when the modal closes so the next open cycle triggers a fresh load.
        effect(() => {
            if (!this.visible()) {
                this.competenciesLoadedForCourseId.set(null);
            }
        });
        // If competencies finish loading and there are none, fall back to free-topic
        effect(() => {
            if (!this.isLoadingCompetencies() && !this.hasCompetencies() && this.isCompetencyMode()) {
                this.mode.set('free-topic');
            }
        });
    }

    private loadCompetencies(courseId: number): void {
        this.isLoadingCompetencies.set(true);
        this.competencySubscription = from(this.courseCompetencyApiService.getCourseCompetenciesByCourseId(courseId))
            .pipe(
                finalize(() => {
                    this.isLoadingCompetencies.set(false);
                    // Mark this courseId as attempted so the effect does not retry on empty list or error.
                    this.competenciesLoadedForCourseId.set(courseId);
                }),
            )
            .subscribe({
                next: (competencies) => this.courseCompetencies.set(competencies),
                error: () => this.alertService.error('artemisApp.quizExercise.aiGeneration.errors.competencyLoadFailed'),
            });
    }

    close(): void {
        this.visible.set(false);
    }

    selectMode(mode: GenerationMode): void {
        this.mode.set(mode);
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

        const difficulty = Math.max(0, Math.min(100, this.difficulty()));
        const request: QuizQuestionGenerationRequest = this.isCompetencyMode()
            ? {
                  competencyIds: this.selectedCompetencies()
                      .map((c) => c.id)
                      .filter((id): id is number => id !== undefined),
                  optionalPrompt: this.optionalPrompt().trim() || undefined,
                  language: this.language(),
                  questionTypes: this.selectedQuestionTypes(),
                  numberOfQuestions: Math.max(1, this.numberOfQuestions() ?? 1),
                  difficulty,
              }
            : {
                  topic: this.topic().trim(),
                  optionalPrompt: this.optionalPrompt().trim() || undefined,
                  language: this.language(),
                  questionTypes: this.selectedQuestionTypes(),
                  numberOfQuestions: Math.max(1, this.numberOfQuestions() ?? 1),
                  difficulty,
              };

        this.startLoadingAnimation();
        this.generationSubscription = this.quizAiGenerationService
            .generateQuizQuestions(courseId, request)
            .pipe(finalize(() => this.stopLoadingAnimation()))
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

    getCurrentLoadingPhraseKey(): string {
        return this.loadingPhraseKeys[this.loadingPhraseIndex()];
    }

    getLoadingDots(): string {
        return '.'.repeat(this.loadingDotsCount());
    }

    ngOnDestroy(): void {
        this.generationSubscription?.unsubscribe();
        this.competencySubscription?.unsubscribe();
        this.stopLoadingAnimation();
    }

    private startLoadingAnimation(): void {
        this.stopLoadingAnimation();
        this.isGenerating.set(true);
        this.loadingPhraseIndex.set(0);
        this.loadingDotsCount.set(0);

        this.loadingPhraseIntervalId = setInterval(() => {
            this.loadingPhraseIndex.update((index) => (index + 1) % this.loadingPhraseKeys.length);
        }, 2200);

        this.loadingDotsIntervalId = setInterval(() => {
            this.loadingDotsCount.update((count) => (count + 1) % 4);
        }, 400);
    }

    private stopLoadingAnimation(): void {
        this.isGenerating.set(false);
        if (this.loadingPhraseIntervalId) {
            clearInterval(this.loadingPhraseIntervalId);
            this.loadingPhraseIntervalId = undefined;
        }
        if (this.loadingDotsIntervalId) {
            clearInterval(this.loadingDotsIntervalId);
            this.loadingDotsIntervalId = undefined;
        }
    }
}
