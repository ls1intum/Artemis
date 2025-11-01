import { Component, ViewEncapsulation, effect, inject, input, output } from '@angular/core';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { QuizQuestion, RenderedQuizQuestionMarkDownElement } from 'app/quiz/shared/entities/quiz-question.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { faExclamationCircle, faExclamationTriangle, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { faCheckSquare, faCircle, faDotCircle, faSquare } from '@fortawesome/free-regular-svg-icons';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbPopover, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { QuizScoringInfoStudentModalComponent } from '../quiz-scoring-infostudent-modal/quiz-scoring-info-student-modal.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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
    // TODO: Map vs. Array --> consistency
    selectedAnswerOptions = input.required<AnswerOption[]>();
    _selectedAnswerOptions: AnswerOption[] = [];
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

    renderedQuestion: RenderedQuizQuestionMarkDownElement;

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
        this.renderedQuestion = new RenderedQuizQuestionMarkDownElement();
        this.renderedQuestion.text = this.artemisMarkdown.safeHtmlForMarkdown(this.question().text);
        this.renderedQuestion.hint = this.artemisMarkdown.safeHtmlForMarkdown(this.question().hint);
        this.renderedQuestion.explanation = this.artemisMarkdown.safeHtmlForMarkdown(this.question().explanation);
        this.renderedQuestion.renderedSubElements = (this.question().answerOptions ?? []).map((answerOption) => {
            const renderedAnswerOption = new RenderedQuizQuestionMarkDownElement();
            renderedAnswerOption.text = this.artemisMarkdown.safeHtmlForMarkdown(answerOption.text);
            renderedAnswerOption.hint = this.artemisMarkdown.safeHtmlForMarkdown(answerOption.hint);
            renderedAnswerOption.explanation = this.artemisMarkdown.safeHtmlForMarkdown(answerOption.explanation);
            return renderedAnswerOption;
        });
    }

    /**
     * Toggles the selection state of a multiple choice answer option
     * @param answerOption The answer option to toggle
     */
    toggleSelection(answerOption: AnswerOption): void {
        if (this.clickDisabled()) {
            // Do nothing
            this._selectedAnswerOptions = this.selectedAnswerOptions();
            return;
        }
        if (this.isAnswerOptionSelected(answerOption)) {
            this._selectedAnswerOptions = this.selectedAnswerOptions().filter((selectedAnswerOption) => {
                if (answerOption.id) {
                    return selectedAnswerOption.id !== answerOption.id;
                }
                return selectedAnswerOption !== answerOption;
            });
        } else if (this.isSingleChoice) {
            this._selectedAnswerOptions = [answerOption];
        } else {
            this._selectedAnswerOptions.push(answerOption);
        }
        this.selectedAnswerOptionsChange.emit(this._selectedAnswerOptions);
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
