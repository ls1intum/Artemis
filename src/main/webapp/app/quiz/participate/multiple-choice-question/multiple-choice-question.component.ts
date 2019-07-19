import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { MultipleChoiceQuestion } from '../../../entities/multiple-choice-question';
import { AnswerOption } from '../../../entities/answer-option';
import { Result } from 'app/entities/result';
import { QuizExercise } from 'app/entities/quiz-exercise';

@Component({
    selector: 'jhi-multiple-choice-question',
    templateUrl: './multiple-choice-question.component.html',
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

    rendered?: MultipleChoiceQuestion;

    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    watchCollection(): void {
        // update html for text, hint and explanation for the question and every answer option
        const artemisMarkdown = this.artemisMarkdown;
        this.rendered = new MultipleChoiceQuestion();
        this.rendered.text = artemisMarkdown.htmlForMarkdownUntrusted(this.question.text);
        this.rendered.hint = artemisMarkdown.htmlForMarkdownUntrusted(this.question.hint);
        this.rendered.explanation = artemisMarkdown.htmlForMarkdownUntrusted(this.question.explanation);
        this.rendered.answerOptions = this.question.answerOptions!.map(answerOption => {
            const renderedAnswerOption = new AnswerOption();
            renderedAnswerOption.text = artemisMarkdown.htmlForMarkdownUntrusted(answerOption.text);
            renderedAnswerOption.hint = artemisMarkdown.htmlForMarkdownUntrusted(answerOption.hint);
            renderedAnswerOption.explanation = artemisMarkdown.htmlForMarkdownUntrusted(answerOption.explanation);
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
