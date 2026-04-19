import { Component, DestroyRef, ViewEncapsulation, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TextareaModule } from 'primeng/textarea';
import { faCircleNotch, faPaperPlane, faWandMagicSparkles, faXmark } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { QuizAiGenerationService } from 'app/quiz/manage/update/quiz-ai-generation-modal/quiz-ai-generation.service';
import { TranslateService } from '@ngx-translate/core';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, finalize } from 'rxjs/operators';

@Component({
    selector: 'jhi-quiz-ai-question-refinement-panel',
    templateUrl: './quiz-ai-question-refinement-panel.component.html',
    styleUrl: './quiz-ai-question-refinement-panel.component.scss',
    encapsulation: ViewEncapsulation.None,
    imports: [FormsModule, ButtonModule, TextareaModule, TranslateDirective, FaIconComponent],
})
export class QuizAiQuestionRefinementPanelComponent {
    private alertService = inject(AlertService);
    private quizAiGenerationService = inject(QuizAiGenerationService);
    private profileService = inject(ProfileService);
    private translateService = inject(TranslateService);
    private destroyRef = inject(DestroyRef);

    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faPaperPlane = faPaperPlane;
    protected readonly faWandMagicSparkles = faWandMagicSparkles;
    protected readonly faCloseMark = faXmark;
    readonly hyperionEnabled: boolean = this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION);

    question = input.required<QuizQuestion>();
    courseId = input.required<number>();
    isOpen = input(false);
    isRefinementPanelCollapsed = input(false);
    /** Reasoning text provided by the parent after a global bulk refinement; shown in place of the per-question reasoning. */
    externalReasoning = input<string | undefined>(undefined);

    questionRefined = output<MultipleChoiceQuestion>();
    /** Emitted when the user dismisses an externally-provided reasoning card. */
    reasoningDismissed = output();

    refinePrompt = signal('');
    isRefining = signal(false);
    refinementExplanation = signal<string | undefined>(undefined);
    promptPlaceholder = signal(this.translateService.instant('artemisApp.quizExercise.aiGeneration.refinement.promptPlaceholder'));

    private refineSubscription?: Subscription;
    private submitSubject = new Subject<void>();

    constructor() {
        this.translateService.onLangChange.pipe(takeUntilDestroyed()).subscribe(() => {
            this.promptPlaceholder.set(this.translateService.instant('artemisApp.quizExercise.aiGeneration.refinement.promptPlaceholder'));
        });

        this.submitSubject.pipe(debounceTime(150), takeUntilDestroyed()).subscribe(() => this.executeRefinement());

        effect(() => {
            if (!this.isOpen()) {
                this.refinePrompt.set('');
                this.refinementExplanation.set(undefined);
                this.refineSubscription?.unsubscribe();
                this.isRefining.set(false);
            }
        });
    }

    /** Clears the local reasoning or notifies the parent to remove the external reasoning for this question. */
    dismissReasoning(): void {
        if (this.refinementExplanation() !== undefined) {
            this.refinementExplanation.set(undefined);
        } else {
            this.reasoningDismissed.emit();
        }
    }

    onEnterKey(event: Event): void {
        if (!(event as KeyboardEvent).shiftKey) {
            event.preventDefault();
            this.submitRefinement();
        }
    }

    submitRefinement(): void {
        const prompt = this.refinePrompt().trim();
        if (!prompt || this.isRefining()) {
            return;
        }
        this.submitSubject.next();
    }

    private executeRefinement(): void {
        const prompt = this.refinePrompt().trim();
        if (!prompt || this.isRefining()) {
            return;
        }

        this.isRefining.set(true);
        this.refineSubscription = this.quizAiGenerationService
            .refineMultipleChoiceQuestion(this.courseId(), this.question() as MultipleChoiceQuestion, prompt)
            .pipe(
                takeUntilDestroyed(this.destroyRef),
                finalize(() => this.isRefining.set(false)),
            )
            .subscribe({
                next: (result) => {
                    const reasoning = result.reasoning?.trim() || this.translateService.instant('artemisApp.quizExercise.aiGeneration.refinement.defaultReasoning');
                    this.refinementExplanation.set(reasoning);
                    this.refinePrompt.set('');
                    this.questionRefined.emit(result.refinedQuestion);
                },
                error: () => {
                    this.alertService.error('artemisApp.quizExercise.aiGeneration.refinement.errors.failed');
                },
            });
    }
}
