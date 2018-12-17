import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { ShortAnswerQuestion } from '../../../entities/short-answer-question';
import { ShortAnswerSpot } from '../../../entities/short-answer-spot';
import { ShortAnswerSolution } from '../../../entities/short-answer-solution';
import { ShortAnswerMapping } from '../../../entities/short-answer-mapping';

import { AnswerOption } from '../../../entities/answer-option';
import { ShortAnswerSubmittedText } from 'app/entities/short-answer-submitted-text';

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

    submittedTexts: ShortAnswerSubmittedText[];

    rendered: ShortAnswerQuestion;
    questionText: String;
    textWithoutSpots: String[];
    textWithOutSpotsFirstParts: String[];
    textWithOutSpotsLastPart: String[];
    input: String = '';

    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    ngOnInit() {}

    ngOnDestroy() {}

    watchCollection() {
        // update html for text, hint and explanation for the question and every answer option
        const artemisMarkdown = this.artemisMarkdown;
        this.rendered = new ShortAnswerQuestion();
        this.questionText = this.question.text.split(/\n/g)[0];
        let questionTextSplitAtNewLine = this.question.text
            .split(/\n/g)
            .slice(3)
            .join();
        this.textWithoutSpots = questionTextSplitAtNewLine.split(/\[-spot\s\d\]/g);
        this.textWithOutSpotsFirstParts = this.textWithoutSpots.slice(0, this.textWithoutSpots.length - 1);
        this.textWithOutSpotsLastPart = this.textWithoutSpots.slice(this.textWithoutSpots.length - 1);

        this.rendered.text = artemisMarkdown.htmlForMarkdown(this.question.text);
        this.rendered.hint = artemisMarkdown.htmlForMarkdown(this.question.hint);
        this.rendered.explanation = artemisMarkdown.htmlForMarkdown(this.question.explanation);
    }

    setSubmittedText(id: number, text: string) {
        let submittedText = new ShortAnswerSubmittedText();
        submittedText.text = text;
        submittedText.spot = this.question.spots[id];

        console.log('test');
        console.log(text);
        console.log(id);

        this.submittedTexts.push(submittedText);
    }
}
