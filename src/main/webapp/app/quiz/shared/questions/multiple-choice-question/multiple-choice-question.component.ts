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

    constructor() {
        effect(() => {
            this.watchCollection();
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
    toggleSelection(answerOption: AnswerOption): void {
        if (this.clickDisabled()) {
            // Do nothing
            return;
        }
        // Always derive the new selection from the current input. The input reflects the options
        // selected so far (including selections loaded from a previously saved submission), so
        // building on top of it preserves existing answers. Mutating a separate local copy here
        // used to drop previously selected options after a reload or component recreation,
        // causing answer loss in exams.
        let updatedSelection: AnswerOption[];
        if (this.isAnswerOptionSelected(answerOption)) {
            updatedSelection = this.selectedAnswerOptions().filter((selectedAnswerOption) => {
                if (answerOption.id) {
                    return selectedAnswerOption.id !== answerOption.id;
                }
                return selectedAnswerOption !== answerOption;
            });
        } else if (this.isSingleChoice) {
            updatedSelection = [answerOption];
        } else {
            updatedSelection = [...this.selectedAnswerOptions(), answerOption];
        }
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
            this.selectedAnswerOptions().findIndex((selectedAnswerOption) => {
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
