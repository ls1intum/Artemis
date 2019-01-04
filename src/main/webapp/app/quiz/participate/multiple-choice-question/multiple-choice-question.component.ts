import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { MultipleChoiceQuestion } from '../../../entities/multiple-choice-question';
import { AnswerOption } from '../../../entities/answer-option';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-multiple-choice-question',
    templateUrl: './multiple-choice-question.component.html',
    providers: [ArtemisMarkdown]
})
export class MultipleChoiceQuestionComponent implements OnInit, OnDestroy {
    _question: MultipleChoiceQuestion;

    @Input()
    set question(question: MultipleChoiceQuestion) {
        this._question = question;
        this.watchCollection();
    }
    get question() {
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

    correctAnswers: number;
    wrongAnswers: number;
    chosenCorrectAnswerOption: number;
    chosenWrongAnswerOption: number;

    @Output()
    selectedAnswerOptionsChange = new EventEmitter();

    rendered: MultipleChoiceQuestion;

    constructor(private artemisMarkdown: ArtemisMarkdown, private modalService: NgbModal) {}

    ngOnInit() {
        this.count();
    }

    ngOnDestroy() {}

    watchCollection() {
        // update html for text, hint and explanation for the question and every answer option
        const artemisMarkdown = this.artemisMarkdown;
        this.rendered = new MultipleChoiceQuestion();
        this.rendered.text = artemisMarkdown.htmlForMarkdown(this.question.text);
        this.rendered.hint = artemisMarkdown.htmlForMarkdown(this.question.hint);
        this.rendered.explanation = artemisMarkdown.htmlForMarkdown(this.question.explanation);
        this.rendered.answerOptions = this.question.answerOptions.map(function(answerOption) {
            const renderedAnswerOption = new AnswerOption();
            renderedAnswerOption.text = artemisMarkdown.htmlForMarkdown(answerOption.text);
            renderedAnswerOption.hint = artemisMarkdown.htmlForMarkdown(answerOption.hint);
            renderedAnswerOption.explanation = artemisMarkdown.htmlForMarkdown(answerOption.explanation);
            return renderedAnswerOption;
        });
    }

    toggleSelection(answerOption: AnswerOption) {
        if (this.clickDisabled) {
            // Do nothing
            return;
        }
        if (this.isAnswerOptionSelected(answerOption)) {
            this.selectedAnswerOptions = this.selectedAnswerOptions.filter(function(selectedAnswerOption) {
                return selectedAnswerOption.id !== answerOption.id;
            });
        } else {
            this.selectedAnswerOptions.push(answerOption);
        }
        this.selectedAnswerOptionsChange.emit(this.selectedAnswerOptions);
        /** Only execute the onSelection function if we received such input **/
        if (this.fnOnSelection) {
            this.fnOnSelection();
        }
    }

    isAnswerOptionSelected(answerOption: AnswerOption) {
        return (
            !!this.selectedAnswerOptions &&
            this.selectedAnswerOptions.findIndex(function(selected) {
                return selected.id === answerOption.id;
            }) !== -1
        );
    }

    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
    }

    count(): number {
        for (let answerOption of this.question.answerOptions) {
            if (!answerOption.invalid && !this.question.invalid && answerOption.isCorrect) {
                if (this.isAnswerOptionSelected(answerOption)) {
                    return this.correctAnswers + 1;
                } else return this.chosenCorrectAnswerOption + 1;
            }

            if (!answerOption.invalid && !this.question.invalid && !answerOption.isCorrect) {
                if (this.isAnswerOptionSelected(answerOption)) {
                    return this.wrongAnswers + 1;
                } else return this.chosenWrongAnswerOption + 1;
            }
        }
    }
}
