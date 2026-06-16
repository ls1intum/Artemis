import { Component, ViewEncapsulation, effect, inject, input, output, signal } from '@angular/core';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { QuizQuestion, RenderedQuizQuestionMarkDownElement } from 'app/quiz/shared/entities/quiz-question.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { faExclamationCircle, faExclamationTriangle, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { faCheckSquare, faCircle, faDotCircle, faSquare } from '@fortawesome/free-regular-svg-icons';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgbPopover, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { QuizScoringInfoStudentModalComponent } from '../quiz-scoring-infostudent-modal/quiz-scoring-info-student-modal.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-multiple-choice-question',
    templateUrl: './multiple-choice-question.component.html',
    styleUrls: ['./multiple-choice-question.component.scss', '../../../overview/participation/quiz-participation.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [NgClass, TranslateDirective, NgbPopover, FaIconComponent, QuizScoringInfoStudentModalComponent, NgbTooltip, ArtemisTranslatePipe],
})
export class MultipleChoiceQuestionComponent {
    private artemisMarkdown = inject(ArtemisMarkdownService);

    question = input.required<MultipleChoiceQuestion>();
    selectedAnswerOptions = input.required<AnswerOption[]>();
    clickDisabled = input<boolean>(false);
    showResult = input<boolean>(false);
    questionIndex = input<number>(0);
    score = input<number>(0);
    forceSampleSolution = input<boolean>(false);
    fnOnSelection = input<any>();
    submittedResult = input<Result>();
    quizQuestions = input<QuizQuestion[] | undefined>();

    selectedAnswerOptionsChange = output<AnswerOption[]>();

    /**
     * Working copy of the current selection that accumulates changes made within a single change-detection frame.
     * It is reset whenever the authoritative `selectedAnswerOptions` input changes, so it never goes stale (which was
     * the original answer-loss bug). It exists so that several rapid toggles within the same frame - before the parent
     * has round-tripped the emitted value back into the input (in zoneless mode the input only updates on the next
     * change-detection cycle) - build on each other instead of each starting from the same stale input and dropping
     * the previous selection.
     */
    private pendingSelectedAnswerOptions?: AnswerOption[];

    constructor() {
        effect(() => {
            this.watchCollection();
        });
        effect(() => {
            // Discard the working copy whenever the authoritative input changes (parent round-trip, reload, recreation),
            // so the next toggle starts from the up-to-date input rather than a stale local copy.
            this.selectedAnswerOptions();
            this.pendingSelectedAnswerOptions = undefined;
        });
    }

    readonly renderedQuestion = signal<RenderedQuizQuestionMarkDownElement>(undefined!);

    // Icons
    faQuestionCircle = faQuestionCircle;
    faExclamationTriangle = faExclamationTriangle;
    faExclamationCircle = faExclamationCircle;
    faSquare = faSquare;
    faCheckSquare = faCheckSquare;
    faCircle = faCircle;
    faDotCircle = faDotCircle;

    /**
     * Update html for text, hint and explanation for the question and every answer option
     */
    watchCollection(): void {
        const renderedQuestion = new RenderedQuizQuestionMarkDownElement();
        renderedQuestion.text = this.artemisMarkdown.safeHtmlForMarkdown(this.question().text);
        renderedQuestion.hint = this.artemisMarkdown.safeHtmlForMarkdown(this.question().hint);
        renderedQuestion.explanation = this.artemisMarkdown.safeHtmlForMarkdown(this.question().explanation);
        renderedQuestion.renderedSubElements = (this.question().answerOptions ?? []).map((answerOption) => {
            const renderedAnswerOption = new RenderedQuizQuestionMarkDownElement();
            renderedAnswerOption.text = this.artemisMarkdown.safeHtmlForMarkdown(answerOption.text);
            renderedAnswerOption.hint = this.artemisMarkdown.safeHtmlForMarkdown(answerOption.hint);
            renderedAnswerOption.explanation = this.artemisMarkdown.safeHtmlForMarkdown(answerOption.explanation);
            return renderedAnswerOption;
        });
        this.renderedQuestion.set(renderedQuestion);
    }

    /**
     * Toggles the selection state of a multiple choice answer option
     * @param answerOption The answer option to toggle
     */
    /**
     * Returns the current selection, preferring the in-frame working copy over the input so that several toggles in the
     * same change-detection frame accumulate correctly instead of each reading the same (not-yet-updated) input.
     */
    private currentSelectedAnswerOptions(): AnswerOption[] {
        return this.pendingSelectedAnswerOptions ?? this.selectedAnswerOptions();
    }

    toggleSelection(answerOption: AnswerOption): void {
        if (this.clickDisabled()) {
            // Do nothing
            return;
        }
        // Always derive the new selection from the current selection (working copy or input). Building on top of it
        // preserves existing answers - including selections loaded from a previously saved submission and selections
        // made by earlier toggles in the same frame. Mutating a separate, never-synced local copy used to drop
        // previously selected options after a reload, recreation, or rapid clicking, causing answer loss in exams.
        const current = this.currentSelectedAnswerOptions();
        let updatedSelection: AnswerOption[];
        if (this.isAnswerOptionSelected(answerOption)) {
            updatedSelection = current.filter((selectedAnswerOption) => {
                if (answerOption.id) {
                    return selectedAnswerOption.id !== answerOption.id;
                }
                return selectedAnswerOption !== answerOption;
            });
        } else if (this.isSingleChoice) {
            updatedSelection = [answerOption];
        } else {
            updatedSelection = [...current, answerOption];
        }
        this.pendingSelectedAnswerOptions = updatedSelection;
        this.selectedAnswerOptionsChange.emit(updatedSelection);
        /** Only execute the onSelection function if we received such input **/
        const fnOnSelectionFn = this.fnOnSelection();
        if (fnOnSelectionFn && typeof fnOnSelectionFn === 'function') {
            fnOnSelectionFn();
        }
    }

    /**
     * Getter whether the given answer option is selected
     * @param answerOption Answer option to be checked for selection state
     */
    isAnswerOptionSelected(answerOption: AnswerOption): boolean {
        return (
            this.currentSelectedAnswerOptions().findIndex((selectedAnswerOption) => {
                if (answerOption.id) {
                    return selectedAnswerOption.id === answerOption.id;
                }
                return selectedAnswerOption === answerOption;
            }) !== -1
        );
    }

    get isSingleChoice() {
        return this.question().singleChoice;
    }
}
