import { Component, ViewEncapsulation, input, output } from '@angular/core';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { faCheck, faExclamationCircle, faExclamationTriangle, faPlus, faQuestionCircle, faTrash, faXmark } from '@fortawesome/free-solid-svg-icons';
import { faCircle } from '@fortawesome/free-regular-svg-icons';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-multiple-choice-visual-question',
    templateUrl: './multiple-choice-visual-question.component.html',
    styleUrls: ['../multiple-choice-question.component.scss', '../../../../overview/participation/quiz-participation.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [TranslateDirective, FormsModule, FaIconComponent, NgbTooltip, NgClass, ArtemisTranslatePipe],
})
export class MultipleChoiceVisualQuestionComponent {
    question = input.required<MultipleChoiceQuestion>();

    questionChanged = output();

    // Icons
    faQuestionCircle = faQuestionCircle;
    faExclamationTriangle = faExclamationTriangle;
    faExclamationCircle = faExclamationCircle;
    faCheck = faCheck;
    faCircle = faCircle;
    faPlus = faPlus;
    faTrash = faTrash;
    faXmark = faXmark;

    parseQuestion() {
        let markdown = this.question().text ?? '';

        if (this.question().hint) {
            markdown += '\n\t[hint] ' + this.question().hint;
        }
        if (this.question().explanation) {
            markdown += '\n\t[exp] ' + this.question().explanation;
        }

        if (this.question().answerOptions && this.question().answerOptions!.length > 0) {
            markdown += '\n';

            this.question().answerOptions!.forEach((answerOption) => {
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
        this.question().answerOptions?.splice(index, 1);

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
        return this.question().singleChoice && this.question().answerOptions?.some((option) => option.isCorrect);
    }

    addNewAnswer() {
        const currentQuestion = this.question();
        if (!currentQuestion.answerOptions) {
            currentQuestion.answerOptions = [];
        }
        currentQuestion.answerOptions.push(new AnswerOption());
        this.question().answerOptions = currentQuestion.answerOptions;

        this.questionChanged.emit();
    }
}
