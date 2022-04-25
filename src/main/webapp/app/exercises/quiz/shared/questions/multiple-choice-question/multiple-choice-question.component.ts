import { Component, EventEmitter, Input, Output, ViewEncapsulation } from '@angular/core';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { RenderedQuizQuestionMarkDownElement } from 'app/entities/quiz/quiz-question.model';
import { Result } from 'app/entities/result.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { faExclamationCircle, faExclamationTriangle, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { faCheckSquare, faSquare, faDotCircle, faCircle } from '@fortawesome/free-regular-svg-icons';

@Component({
    selector: 'jhi-multiple-choice-question',
    templateUrl: './multiple-choice-question.component.html',
    styleUrls: ['./multiple-choice-question.component.scss', '../../../participate/quiz-participation.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class MultipleChoiceQuestionComponent {
    _question: MultipleChoiceQuestion;

    @Input()
    set question(question: MultipleChoiceQuestion) {
        this._question = question;
        this.watchCollection();
    }
    get question(): MultipleChoiceQuestion {
        return this._question;
    }
    // TODO: Map vs. Array --> consistency
    @Input()
    selectedAnswerOptions: AnswerOption[];
    @Input()
    clickDisabled: boolean;
    @Input()
    showResult: boolean;
    @Input()
    questionIndex: number;
    @Input()
    score: number;
    @Input()
    forceSampleSolution: boolean;
    @Input()
    fnOnSelection: any;
    @Input()
    submittedResult: Result;
    @Input()
    submittedQuizExercise: QuizExercise;

    @Output()
    selectedAnswerOptionsChange = new EventEmitter<AnswerOption[]>();

    renderedQuestion: RenderedQuizQuestionMarkDownElement;

    // Icons
    faQuestionCircle = faQuestionCircle;
    faExclamationTriangle = faExclamationTriangle;
    faExclamationCircle = faExclamationCircle;
    faSquare = faSquare;
    faCheckSquare = faCheckSquare;
    faCircle = faCircle;
    faDotCircle = faDotCircle;

    constructor(private artemisMarkdown: ArtemisMarkdownService) {}

    /**
     * Update html for text, hint and explanation for the question and every answer option
     */
    watchCollection(): void {
        this.renderedQuestion = new RenderedQuizQuestionMarkDownElement();
        this.renderedQuestion.text = this.artemisMarkdown.safeHtmlForMarkdown(this.question.text);
        this.renderedQuestion.hint = this.artemisMarkdown.safeHtmlForMarkdown(this.question.hint);
        this.renderedQuestion.explanation = this.artemisMarkdown.safeHtmlForMarkdown(this.question.explanation);
        this.renderedQuestion.renderedSubElements = this.question.answerOptions!.map((answerOption) => {
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
        if (this.clickDisabled) {
            // Do nothing
            return;
        }
        if (this.isAnswerOptionSelected(answerOption)) {
            this.selectedAnswerOptions = this.selectedAnswerOptions.filter((selectedAnswerOption) => selectedAnswerOption.id !== answerOption.id);
        } else if (this.isSingleChoice) {
            this.selectedAnswerOptions = [answerOption];
        } else {
            this.selectedAnswerOptions.push(answerOption);
        }
        this.selectedAnswerOptionsChange.emit(this.selectedAnswerOptions);
        /** Only execute the onSelection function if we received such input **/
        if (this.fnOnSelection) {
            this.fnOnSelection();
        }
    }

    /**
     * Getter whether the given answer option is selected
     * @param answerOption Answer option to be checked for selection state
     */
    isAnswerOptionSelected(answerOption: AnswerOption): boolean {
        return (
            this.selectedAnswerOptions.findIndex((selected) => {
                return selected.id === answerOption.id;
            }) !== -1
        );
    }

    get isSingleChoice() {
        return this.question.singleChoice;
    }
}
