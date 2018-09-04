import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import {MultipleChoiceQuestion} from '../../../entities/multiple-choice-question';
import {AnswerOption} from '../../../entities/answer-option';

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
    @Input() selectedAnswerOptions: AnswerOption[];
    @Input() clickDisabled: boolean;
    @Input() showResult: boolean;
    @Input() questionIndex: number;
    @Input() score: number;
    @Input() forceSampleSolution: boolean;
    @Input() fnOnSelection: any;

    @Output() selectedAnswerOptionsChange = new EventEmitter();

    rendered: any;

    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    ngOnInit() {}

    ngOnDestroy() {
    }

    watchCollection() {
        // update html for text, hint and explanation for the question and every answer option
        const artemisMarkdown = this.artemisMarkdown;
        this.rendered = {
            text: artemisMarkdown.htmlForMarkdown(this.question.text),
            hint: artemisMarkdown.htmlForMarkdown(this.question.hint),
            explanation: artemisMarkdown.htmlForMarkdown(this.question.explanation),
            answerOptions: this.question.answerOptions.map(function(answerOption) {
                return {
                    text: artemisMarkdown.htmlForMarkdown(answerOption.text),
                    hint: artemisMarkdown.htmlForMarkdown(answerOption.hint),
                    explanation: artemisMarkdown.htmlForMarkdown(answerOption.explanation)
                };
            })
        };
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
        // Note: I had to add a timeout of 0ms here, because the model changes are propagated asynchronously,
        // so we wait for one javascript event cycle before we inform the parent of changes
        setTimeout( () => { this.fnOnSelection(); }, 0);
    }

    isAnswerOptionSelected(answerOption: AnswerOption) {
        return !!this.selectedAnswerOptions && this.selectedAnswerOptions.findIndex(function(selected) {
            return selected.id === answerOption.id;
        }) !== -1;
    }
}
