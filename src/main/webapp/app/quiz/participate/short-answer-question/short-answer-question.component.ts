import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, ViewEncapsulation } from '@angular/core';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { ShortAnswerQuestionUtil } from 'app/components/util/short-answer-question-util.service';
import { ShortAnswerSolution } from 'app/entities/short-answer-solution/short-answer-solution.model';
import { ShortAnswerQuestion } from 'app/entities/short-answer-question/short-answer-question.model';
import { ShortAnswerSubmittedText } from 'app/entities/short-answer-submitted-text/short-answer-submitted-text.model';
import { RenderedQuizQuestionMarkDownElement } from 'app/entities/quiz-question/quiz-question.model';

@Component({
    selector: 'jhi-short-answer-question',
    templateUrl: './short-answer-question.component.html',
    providers: [ArtemisMarkdown, ShortAnswerQuestionUtil],
    styleUrls: ['./short-answer-question.component.scss', '../quiz-question.scss'],
    encapsulation: ViewEncapsulation.None,
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
    renderedQuestion: RenderedQuizQuestionMarkDownElement;
    sampleSolutions: ShortAnswerSolution[] = [];
    textParts: (string | null)[][];

    constructor(private artemisMarkdown: ArtemisMarkdown, public shortAnswerQuestionUtil: ShortAnswerQuestionUtil) {}

    ngOnInit() {}

    ngOnDestroy() {}

    watchCollection() {
        // update html for text, hint and explanation for the question and every answer option
        const artemisMarkdown = this.artemisMarkdown;
        this.renderedQuestion = new RenderedQuizQuestionMarkDownElement();

        // new way
        const textParts = this.shortAnswerQuestionUtil.divideQuestionTextIntoTextParts(this.question.text!);
        this.textParts = this.shortAnswerQuestionUtil.transformTextPartsIntoHTML(textParts, this.artemisMarkdown);

        this.renderedQuestion.text = artemisMarkdown.safeHtmlForMarkdown(this.question.text);
        this.renderedQuestion.hint = artemisMarkdown.safeHtmlForMarkdown(this.question.hint);
        this.renderedQuestion.explanation = artemisMarkdown.safeHtmlForMarkdown(this.question.explanation);
    }

    /**
     * When students type in their answers and the focus gets away from the input spot, the answers are
     * set as submitted texts
     */
    setSubmittedText() {
        this.submittedTexts = [];
        let i = 0;
        for (const textpart of this.textParts) {
            let j = 0;
            for (const element of textpart) {
                if (this.shortAnswerQuestionUtil.isInputField(element!)) {
                    const submittedText = new ShortAnswerSubmittedText();
                    submittedText.text = (<HTMLInputElement>document.getElementById('solution-' + i + '-' + j + '-' + this._question.id)).value;
                    submittedText.spot = this.shortAnswerQuestionUtil.getSpot(this.shortAnswerQuestionUtil.getSpotNr(element!), this.question);
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
        const index = this.question.spots.findIndex(spot => spot.spotNr === this.shortAnswerQuestionUtil.getSpotNr(spotTag));
        return this.sampleSolutions[index];
    }

    getBackgroundColourForInputField(spotTag: string): string {
        if (this.getSubmittedTextForSpot(spotTag) === undefined) {
            return 'red';
        }
        return this.getSubmittedTextForSpot(spotTag).isCorrect ? (this.isSubmittedTextCompletelyCorrect(spotTag) ? 'lightgreen' : 'yellow') : 'red';
    }

    isSubmittedTextCompletelyCorrect(spotTag: string): boolean {
        let isTextCorrect = false;
        const solutionsForSpot = this.shortAnswerQuestionUtil.getAllSolutionsForSpot(
            this.question.correctMappings,
            this.shortAnswerQuestionUtil.getSpot(this.shortAnswerQuestionUtil.getSpotNr(spotTag), this.question),
        );

        if (solutionsForSpot.filter(solution => solution.text === this.getSubmittedTextForSpot(spotTag).text).length > 0) {
            isTextCorrect = true;
        }
        return isTextCorrect;
    }
}
