import { Component, EventEmitter, Input, Output, ViewEncapsulation } from '@angular/core';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { faCheck, faExclamationCircle, faExclamationTriangle, faPlus, faQuestionCircle, faTrash, faXmark } from '@fortawesome/free-solid-svg-icons';
import { faCircle } from '@fortawesome/free-regular-svg-icons';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';

@Component({
    selector: 'jhi-multiple-choice-visual-question',
    templateUrl: './multiple-choice-visual-question.component.html',
    styleUrls: ['./multiple-choice-question.component.scss', '../../../participate/quiz-participation.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class MultipleChoiceVisualQuestionComponent {
    _question: MultipleChoiceQuestion;

    @Input()
    set question(question: MultipleChoiceQuestion) {
        this._question = question;
    }
    get question(): MultipleChoiceQuestion {
        return this._question;
    }

    @Output() questionChanged = new EventEmitter();

    // Icons
    faQuestionCircle = faQuestionCircle;
    faExclamationTriangle = faExclamationTriangle;
    faExclamationCircle = faExclamationCircle;
    faCheck = faCheck;
    faCircle = faCircle;
    faPlus = faPlus;
    faTrash = faTrash;
    faXmark = faXmark;

    constructor() {}

    parseQuestion() {
        let markdown = this.question.text ?? '';

        if (this.question.hint) {
            markdown += '\n\t[hint] ' + this.question.hint;
        }
        if (this.question.explanation) {
            markdown += '\n\t[exp] ' + this.question.explanation;
        }

        if (this.question.answerOptions && this.question.answerOptions.length > 0) {
            markdown += '\n';

            this.question.answerOptions.forEach((answerOption) => {
                markdown += '\n' + (answerOption.isCorrect ? '[correct] ' : '[wrong] ') + answerOption.text;

                if (answerOption.hint) {
                    markdown += '\n\t[hint] ' + answerOption.hint;
                }
                if (answerOption.explanation) {
                    markdown += '\n\t[exp] ' + answerOption.explanation;
                }
            });
        }

        return markdown;
    }

    deleteAnswer(index: number) {
        this.question.answerOptions?.splice(index, 1);

        this.questionChanged.emit();
    }

    toggleIsCorrect(answerOption: AnswerOption) {
        if (this.correctToggleDisabled() && !answerOption.isCorrect) {
            return;
        }

        answerOption.isCorrect = !answerOption.isCorrect;

        this.questionChanged.emit();
    }

    correctToggleDisabled() {
        return this.question.singleChoice && this.question.answerOptions?.some((option) => option.isCorrect);
    }

    addNewAnswer() {
        if (this.question.answerOptions === undefined) {
            this.question.answerOptions = [];
        }

        this.question.answerOptions?.push(new AnswerOption());

        this.questionChanged.emit();
    }
}
