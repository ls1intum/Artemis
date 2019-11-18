import { Component, EventEmitter, Input, Output, ViewEncapsulation } from '@angular/core';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { MultipleChoiceQuestion } from '../../../entities/multiple-choice-question';
import { AnswerOption } from '../../../entities/answer-option';
import { Result } from 'app/entities/result';
import { QuizExercise } from 'app/entities/quiz-exercise';
import { RenderedQuizQuestionMarkDownElement } from 'app/entities/quiz-question';

@Component({
    selector: 'jhi-multiple-choice-question',
    templateUrl: './multiple-choice-question.component.html',
    styleUrls: ['./multiple-choice-question.component.scss', '../quiz-question.scss'],
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
        this.renderedQuestion.text = artemisMarkdown.htmlForMarkdown(this.question.text);
        this.renderedQuestion.hint = artemisMarkdown.htmlForMarkdown(this.question.hint);
        this.renderedQuestion.explanation = artemisMarkdown.htmlForMarkdown(this.question.explanation);
        this.renderedQuestion.renderedSubElements = this.question.answerOptions!.map(answerOption => {
            const renderedAnswerOption = new RenderedQuizQuestionMarkDownElement();
            renderedAnswerOption.text = artemisMarkdown.htmlForMarkdown(answerOption.text);
            renderedAnswerOption.hint = artemisMarkdown.htmlForMarkdown(answerOption.hint);
            renderedAnswerOption.explanation = artemisMarkdown.htmlForMarkdown(answerOption.explanation);
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
