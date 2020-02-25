import { Component, EventEmitter, Input, Output, ViewEncapsulation } from '@angular/core';
import { ArtemisMarkdown } from 'app/shared/markdown.service';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { RenderedQuizQuestionMarkDownElement } from 'app/entities/quiz/quiz-question.model';
import { Result } from 'app/entities/result.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

@Component({
    selector: 'jhi-multiple-choice-question',
    templateUrl: './multiple-choice-question.component.html',
    styleUrls: ['./multiple-choice-question.component.scss', '../../../participate/quiz-participation.scss'],
    encapsulation: ViewEncapsulation.None,
    providers: [ArtemisMarkdown],
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
    selectedAnswerOptionsChange = new EventEmitter();

    renderedQuestion: RenderedQuizQuestionMarkDownElement;

    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    watchCollection(): void {
        // update html for text, hint and explanation for the question and every answer option
        const artemisMarkdown = this.artemisMarkdown;
        this.renderedQuestion = new RenderedQuizQuestionMarkDownElement();
        this.renderedQuestion.text = artemisMarkdown.safeHtmlForMarkdown(this.question.text);
        this.renderedQuestion.hint = artemisMarkdown.safeHtmlForMarkdown(this.question.hint);
        this.renderedQuestion.explanation = artemisMarkdown.safeHtmlForMarkdown(this.question.explanation);
        this.renderedQuestion.renderedSubElements = this.question.answerOptions!.map(answerOption => {
            const renderedAnswerOption = new RenderedQuizQuestionMarkDownElement();
            renderedAnswerOption.text = artemisMarkdown.safeHtmlForMarkdown(answerOption.text);
            renderedAnswerOption.hint = artemisMarkdown.safeHtmlForMarkdown(answerOption.hint);
            renderedAnswerOption.explanation = artemisMarkdown.safeHtmlForMarkdown(answerOption.explanation);
            return renderedAnswerOption;
        });
    }

    toggleSelection(answerOption: AnswerOption): void {
        if (this.clickDisabled) {
            // Do nothing
            return;
        }
        if (this.isAnswerOptionSelected(answerOption)) {
            this.selectedAnswerOptions = this.selectedAnswerOptions.filter(selectedAnswerOption => selectedAnswerOption.id !== answerOption.id);
        } else {
            this.selectedAnswerOptions.push(answerOption);
        }
        this.selectedAnswerOptionsChange.emit(this.selectedAnswerOptions);
        /** Only execute the onSelection function if we received such input **/
        if (this.fnOnSelection) {
            this.fnOnSelection();
        }
    }

    isAnswerOptionSelected(answerOption: AnswerOption): boolean {
        return (
            this.selectedAnswerOptions != null &&
            this.selectedAnswerOptions.findIndex(function(selected) {
                return selected.id === answerOption.id;
            }) !== -1
        );
    }
}
