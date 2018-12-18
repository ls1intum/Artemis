import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { ShortAnswerQuestion } from '../../../entities/short-answer-question';
import { ShortAnswerSpot } from '../../../entities/short-answer-spot';
import { ShortAnswerSolution } from '../../../entities/short-answer-solution';
import { ShortAnswerMapping } from '../../../entities/short-answer-mapping';
import { ShortAnswerSubmittedText } from 'app/entities/short-answer-submitted-text';
import { ShortAnswerSubmittedAnswer } from 'app/entities/short-answer-submitted-answer';

@Component({
    selector: 'jhi-short-answer-question',
    templateUrl: './short-answer-question.component.html',
    providers: [ArtemisMarkdown]
})
export class ShortAnswerQuestionComponent implements OnInit, OnDestroy {
    _question: ShortAnswerQuestion;
    _forceSampleSolution: boolean;

    @Input()
    set question(question: ShortAnswerQuestion) {
        this._question = question;
        this.watchCollection();
    }

    get question() {
        return this._question;
    }

    @Input()
    submittedTexts: ShortAnswerSubmittedText[];
    @Input()
    clickDisabled: boolean;
    @Input()
    showResult: boolean;
    @Input()
    questionIndex: number;
    @Input()
    score: number;
    @Input()
    set forceSampleSolution(forceSampleSolution) {
        this._forceSampleSolution = forceSampleSolution;
        if (this.forceSampleSolution) {
            console.log('ForceSampleSolution activated');
            //TODO:FDE
            // this.showSampleSolution();
        }
    }
    get forceSampleSolution() {
        return this._forceSampleSolution;
    }
    @Input()
    fnOnSubmittedTextUpdate: any;

    //TODO: FDE Check if ok so
    @Output()
    submittedTextsChange = new EventEmitter();

    showingSampleSolution = false;
    rendered: ShortAnswerQuestion;
    questionText: string;
    textWithoutSpots: string[];
    textWithOutSpotsFirstParts: string[];
    textWithOutSpotsLastPart: string[];

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

    setSubmittedText() {
        console.log('test1');
        for (let id in this.textWithOutSpotsFirstParts) {
            let submittedText = new ShortAnswerSubmittedText();
            submittedText.text = (<HTMLInputElement>document.getElementById('solution-' + id)).value;
            submittedText.spot = this.question.spots[id];
            console.log('test2');
            console.log(id);
            console.log(submittedText.text);
            console.log(submittedText.spot);
            this.submittedTexts.push(submittedText);
        }
        console.log(this.submittedTexts);

        this.submittedTextsChange.emit(this.submittedTexts);
        /** Only execute the onMappingUpdate function if we received such input **/
        if (this.fnOnSubmittedTextUpdate) {
            this.fnOnSubmittedTextUpdate();
        }
    }
}
