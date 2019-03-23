import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { ShortAnswerQuestion } from '../../../entities/short-answer-question';
import { ShortAnswerSolution } from '../../../entities/short-answer-solution';
import { ShortAnswerSubmittedText } from 'app/entities/short-answer-submitted-text';
import { ShortAnswerQuestionUtil } from '../../../components/util/short-answer-question-util.service';
import {ShortAnswerSpot} from "app/entities/short-answer-spot";

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
    textParts: String [][];

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




        this.textParts = this.question.text.split(/\n/g).map(t => t.split(/\s+(?![^[]]*])/g));

        this.textParts = this.textParts.map(textPart =>
            textPart.map(element =>
                 element = artemisMarkdown.htmlForMarkdown(element.toString())));

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
        /*
        for (const id of Object.keys(this.textBeforeSpots)) {
            const submittedText = new ShortAnswerSubmittedText();
            submittedText.text = (<HTMLInputElement>document.getElementById('solution-' + id + '-' + this._question.id)).value;
            submittedText.spot = this.question.spots[id];
            this.submittedTexts.push(submittedText);
        } */

        console.log('länge von submittedTexts' + this.submittedTexts.length);
        console.log(this.submittedTexts);

        let i = 0;
        for (const textpart of this.textParts) {
            let j = 0;
            for (const element of textpart) {
                console.log(element.toString());
                console.log(this.isInputField(element.toString()));
                if (this.isInputField(element.toString())) {
                    const submittedText = new ShortAnswerSubmittedText();
                    console.log('solution-' + i + '-' + j + '-' + this._question.id);
                    console.log(this.getSpot(this.getSpotNr(element.toString())));
                    submittedText.text = (<HTMLInputElement>document.getElementById('solution-' + i + '-' + j + '-' + this._question.id)).value;
                    submittedText.spot = this.getSpot(this.getSpotNr(element.toString()));
                    console.log(submittedText);
                    this.submittedTexts.push(submittedText);
                }
                j++;
            }
            i++;
        }


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
        // TODO: the question is not yet available
        this.sampleSolutions = this.shortAnswerQuestionUtil.getSampleSolution(this.question);
        this.showingSampleSolution = true;
    }

    /**
     * Display the student's answer again
     */
    hideSampleSolution() {
        this.showingSampleSolution = false;
    }

    getSubmittedTextForSpot(spotTag: string): ShortAnswerSubmittedText {
        return this.submittedTexts.filter(submittedText => submittedText.spot.spotNr === this.getSpotNr(spotTag))[0];
    }

    getSampleSolutionForSpot(spotTag: string): ShortAnswerSolution {
        console.log(this.sampleSolutions);
        console.log(this.question.correctMappings.filter(mapping => mapping.spot.spotNr === this.getSpotNr(spotTag)));
        console.log(this.sampleSolutions.filter(
            solution => solution.id === this.question.correctMappings.filter(
                mapping => mapping.spot.spotNr === this.getSpotNr(spotTag.toString()))[0].solution.id));

        return this.sampleSolutions.filter(
            solution => solution.id === this.question.correctMappings.filter(
                mapping => mapping.spot.spotNr === this.getSpotNr(spotTag.toString()))[0].solution.id)[0];
    }

    isSubmittedTextCompletelyCorrect(spotTag: string): boolean {
        let isTextCorrect = false;
        const solutionsForSpot = this.shortAnswerQuestionUtil.getAllSolutionsForSpot(this.question.correctMappings, this.getSpot(this.getSpotNr(spotTag)));

        if (solutionsForSpot.filter(solution => solution.text === this.getSubmittedTextForSpot(spotTag).text).length > 0) {
            isTextCorrect = true;
        }
        return isTextCorrect;
    }


    // add functions below to util class
    /**
     * checks if text is an input field (check for spot tag)
     * @param text
     */
    isInputField(text: string): boolean {
        return !(text.search(/\[-spot/g) === -1);
    }

    /**
     * gets just the spot number
     * @param text
     */
    getSpotNr(text: string): number {
        return +text.split(/\s+/g).slice(1).join().split(/\]/g)[0].trim();
    }

    /**
     * gets the spot for a specific spotNr
     * @param spotNr
     */
    getSpot(spotNr: number): ShortAnswerSpot  {
        return this.question.spots.filter(spot => spot.spotNr === spotNr)[0];
    }
}
