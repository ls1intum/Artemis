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
    sampleSolutions: ShortAnswerSolution[] =  [];
    textParts: string[][];

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

        // new way
        this.textParts = this.shortAnswerQuestionUtil.divideQuestionTextIntoTextParts(this.question.text);
        this.textParts = this.shortAnswerQuestionUtil.transformTextPartsIntoHTML(this.textParts, this.artemisMarkdown);

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
        console.log('länge von submittedTexts' + this.submittedTexts.length);
        console.log(this.submittedTexts);

        let i = 0;
        for (const textpart of this.textParts) {
            let j = 0;
            for (const element of textpart) {
                console.log(element);
                console.log(this.shortAnswerQuestionUtil.isInputField(element));
                if (this.shortAnswerQuestionUtil.isInputField(element)) {
                    const submittedText = new ShortAnswerSubmittedText();
                    console.log('solution-' + i + '-' + j + '-' + this._question.id);
                    console.log(this.shortAnswerQuestionUtil.getSpot(this.shortAnswerQuestionUtil.getSpotNr(element), this.question));
                    submittedText.text = (<HTMLInputElement>document.getElementById('solution-' + i + '-' + j + '-' + this._question.id)).value;
                    submittedText.spot = this.shortAnswerQuestionUtil.getSpot(this.shortAnswerQuestionUtil.getSpotNr(element), this.question);
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
        return this.submittedTexts.filter(submittedText => submittedText.spot.spotNr === this.shortAnswerQuestionUtil.getSpotNr(spotTag))[0];
    }

    getSampleSolutionForSpot(spotTag: string): ShortAnswerSolution {
        console.log(this.sampleSolutions);
        console.log(this.question.correctMappings.filter(mapping => mapping.spot.spotNr === this.shortAnswerQuestionUtil.getSpotNr(spotTag)));
        console.log(this.sampleSolutions.filter(
            solution => solution.id === this.question.correctMappings.filter(
                mapping => mapping.spot.spotNr === this.shortAnswerQuestionUtil.getSpotNr(spotTag.toString()))[0].solution.id));

        return this.sampleSolutions.filter(
            solution => solution.id === this.question.correctMappings.filter(
                mapping => mapping.spot.spotNr === this.shortAnswerQuestionUtil.getSpotNr(spotTag.toString()))[0].solution.id)[0];
    }

    getBackgroundColourForInputField(spotTag: string): string {
        if(this.getSubmittedTextForSpot(spotTag) === undefined) {
            return 'red';
        }
       return this.getSubmittedTextForSpot(spotTag).isCorrect ? (this.isSubmittedTextCompletelyCorrect(spotTag) ? 'lightgreen' : 'yellow') : 'red';
    }

    isSubmittedTextCompletelyCorrect(spotTag: string): boolean {
        let isTextCorrect = false;
        const solutionsForSpot = this.shortAnswerQuestionUtil.getAllSolutionsForSpot(this.question.correctMappings,
            this.shortAnswerQuestionUtil.getSpot(this.shortAnswerQuestionUtil.getSpotNr(spotTag), this.question));

        if (solutionsForSpot.filter(solution => solution.text === this.getSubmittedTextForSpot(spotTag).text).length > 0) {
            isTextCorrect = true;
        }
        return isTextCorrect;
    }
}
