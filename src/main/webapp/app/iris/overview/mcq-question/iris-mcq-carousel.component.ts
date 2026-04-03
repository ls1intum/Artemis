import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { McqResponseData, McqSetData } from 'app/iris/shared/entities/iris-content-type.model';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { IrisMcqQuestionComponent } from './iris-mcq-question.component';

/**
 * Carousel component that displays a set of multiple-choice questions with navigation,
 * tracks answers per question, and shows a score summary when all questions are answered.
 */
@Component({
    selector: 'jhi-iris-mcq-carousel',
    standalone: true,
    imports: [IrisMcqQuestionComponent, TranslateDirective, ArtemisTranslatePipe, FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    templateUrl: './iris-mcq-carousel.component.html',
    styleUrl: './iris-mcq-carousel.component.scss',
})
export class IrisMcqCarouselComponent {
    readonly faChevronLeft = faChevronLeft;
    readonly faChevronRight = faChevronRight;

    mcqSetData = input.required<McqSetData>();
    messageId = input<number>();
    citationInfo = input<IrisCitationMetaDTO[]>([]);

    responseSaved = output<McqResponseData>();

    currentIndex = signal(0);
    showScore = signal(false);
    answers = signal<ReadonlyMap<number, { selectedIndex: number; submitted: boolean }>>(new Map());

    private static readonly DOT_THRESHOLD = 10;

    totalQuestions = computed(() => this.mcqSetData().questions.length);
    useDots = computed(() => this.totalQuestions() <= IrisMcqCarouselComponent.DOT_THRESHOLD);

    answeredCount = computed(() => {
        let count = 0;
        for (const a of this.answers().values()) {
            if (a.submitted) {
                count++;
            }
        }
        return count;
    });

    allAnswered = computed(() => this.answeredCount() === this.totalQuestions());

    correctCount = computed(() => {
        const questions = this.mcqSetData().questions;
        let count = 0;
        for (const [idx, a] of this.answers().entries()) {
            if (a.submitted && questions[idx]?.options[a.selectedIndex]?.correct) {
                count++;
            }
        }
        return count;
    });

    scorePercentage = computed(() => {
        const total = this.totalQuestions();
        return total > 0 ? Math.round((this.correctCount() / total) * 100) : 0;
    });

    currentAnswer = computed(() => this.answers().get(this.currentIndex()));

    constructor() {
        effect(() => {
            const data = this.mcqSetData();
            if (data.responses?.length) {
                const restored = new Map<number, { selectedIndex: number; submitted: boolean }>();
                for (const r of data.responses) {
                    if (r.questionIndex !== undefined) {
                        restored.set(r.questionIndex, { selectedIndex: r.selectedIndex, submitted: r.submitted });
                    }
                }
                this.answers.set(restored);
                if (restored.size === data.questions?.length) {
                    this.showScore.set(true);
                }
            }
        });
    }

    goToQuestion(index: number): void {
        if (index >= 0 && index < this.totalQuestions()) {
            this.currentIndex.set(index);
        }
    }

    nextQuestion(): void {
        this.goToQuestion(this.currentIndex() + 1);
    }

    previousQuestion(): void {
        this.goToQuestion(this.currentIndex() - 1);
    }

    onAnswerChanged(event: { selectedIndex: number | undefined; submitted: boolean }): void {
        if (event.selectedIndex === undefined) {
            return;
        }
        const updated = new Map(this.answers());
        updated.set(this.currentIndex(), { selectedIndex: event.selectedIndex, submitted: event.submitted });
        this.answers.set(updated);

        if (event.submitted) {
            this.responseSaved.emit({
                selectedIndex: event.selectedIndex,
                submitted: true,
                questionIndex: this.currentIndex(),
            });
        }
    }

    isQuestionCorrect(index: number): boolean {
        const answer = this.answers().get(index);
        if (!answer?.submitted) {
            return false;
        }
        return this.mcqSetData().questions[index]?.options[answer.selectedIndex]?.correct ?? false;
    }

    isQuestionIncorrect(index: number): boolean {
        const answer = this.answers().get(index);
        if (!answer?.submitted) {
            return false;
        }
        return !this.isQuestionCorrect(index);
    }
}
