import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { ShortAnswerQuestion } from '../../../entities/short-answer-question';
import { ShortAnswerSpot } from '../../../entities/short-answer-spot';
import { ShortAnswerSolution } from '../../../entities/short-answer-solution';
import { ShortAnswerMapping } from '../../../entities/short-answer-mapping';

import { AnswerOption } from '../../../entities/answer-option';

@Component({
    selector: 'jhi-short-answer-question',
    templateUrl: './short-answer-question.component.html',
    providers: [ArtemisMarkdown]
})
export class ShortAnswerQuestionComponent implements OnInit, OnDestroy {
    _question: ShortAnswerQuestion;

    @Input()
    set question(question: ShortAnswerQuestion) {
        this._question = question;
        this.watchCollection();
    }

    get question() {
        return this._question;
    }

    @Input()
    mappings: ShortAnswerMapping[];
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

    //TODO: FDE Check if ok so
    @Output()
    mappingssChange = new EventEmitter();

    rendered: ShortAnswerQuestion;

    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    ngOnInit() {}

    ngOnDestroy() {}

    watchCollection() {
        // update html for text, hint and explanation for the question and every answer option
        const artemisMarkdown = this.artemisMarkdown;
        this.rendered = new ShortAnswerQuestion();
        this.rendered.text = artemisMarkdown.htmlForMarkdown(this.question.text);
        this.rendered.hint = artemisMarkdown.htmlForMarkdown(this.question.hint);
        this.rendered.explanation = artemisMarkdown.htmlForMarkdown(this.question.explanation);
    }
}
