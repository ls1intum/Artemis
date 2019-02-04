import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { ShortAnswerQuestion } from '../../../entities/short-answer-question';
import { ShortAnswerSolution } from '../../../entities/short-answer-solution';
import { ShortAnswerSubmittedText } from 'app/entities/short-answer-submitted-text';
import { ShortAnswerQuestionUtil } from '../../../components/util/short-answer-question-util.service';

@Component({
    selector: 'jhi-short-answer-question',
    templateUrl: './short-answer-question.component.html',
    providers: [ArtemisMarkdown, ShortAnswerQuestionUtil]
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
            this.showSampleSolution();
        }
    }

    get forceSampleSolution() {
        return this._forceSampleSolution;
    }
    @Input()
    fnOnSubmittedTextUpdate: any;

    @Output()
    submittedTextsChange = new EventEmitter();
    showingSampleSolution = false;
    rendered: ShortAnswerQuestion;
    questionText: string;
    textWithoutSpots: string[];
    textBeforeSpots: string[];
    textAfterSpots: string[];
    sampleSolutions: ShortAnswerSolution[] =  [];
    solutionID = 1;

    constructor(
        private artemisMarkdown: ArtemisMarkdown,
        private shortAnswerQuestionUtil: ShortAnswerQuestionUtil
    ) {}

    ngOnInit() {}

    ngOnDestroy() {}

    watchCollection() {
        // update html for text, hint and explanation for the question and every answer option
        const artemisMarkdown = this.artemisMarkdown;
        this.rendered = new ShortAnswerQuestion();

        // is either '' or the question in the first line
        this.questionText = this.shortAnswerQuestionUtil.firstLineOfQuestion(this.question.text);
        this.textWithoutSpots = this.shortAnswerQuestionUtil.getTextWithoutSpots(this.question.text);

        // separates the text into parts that come before the spot tag
        this.textBeforeSpots = this.textWithoutSpots.slice(0, this.textWithoutSpots.length - 1);

        // the last part that comes after the last spot tag
        this.textAfterSpots = this.textWithoutSpots.slice(this.textWithoutSpots.length - 1);

        this.rendered.text = artemisMarkdown.htmlForMarkdown(this.question.text);
        this.rendered.hint = artemisMarkdown.htmlForMarkdown(this.question.hint);
        this.rendered.explanation = artemisMarkdown.htmlForMarkdown(this.question.explanation);
    }

    /**
     * When students type in their answers and the focus gets away from the input spot, the answers are
     * set as submitted texts
     */
    setSubmittedText() {
        this.submittedTexts = [];
        for (const id of Object.keys(this.textBeforeSpots)) {
            const submittedText = new ShortAnswerSubmittedText();
            submittedText.text = (<HTMLInputElement>document.getElementById('solution-' + (this.solutionID + +id))).value;
            submittedText.spot = this.question.spots[id];
            this.submittedTexts.push(submittedText);
        }
        this.solutionID += this.textBeforeSpots.length;
        this.submittedTextsChange.emit(this.submittedTexts);
        /** Only execute the onMappingUpdate function if we received such input **/
        if (this.fnOnSubmittedTextUpdate) {
            this.fnOnSubmittedTextUpdate();
        }
    }

    /**
     * Display a sample solution instead of the student's answer
     */
    showSampleSolution() {
        this.sampleSolutions = this.shortAnswerQuestionUtil.getSampleSolution(this.question);
        this.showingSampleSolution = true;
    }

    /**
     * Display the student's answer again
     */
    hideSampleSolution() {
        this.showingSampleSolution = false;
    }
}
