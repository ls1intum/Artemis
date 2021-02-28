import { Component, EventEmitter, Input, Output, ViewEncapsulation } from '@angular/core';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { RenderedQuizQuestionMarkDownElement } from 'app/entities/quiz/quiz-question.model';

@Component({
    selector: 'jhi-short-answer-question',
    templateUrl: './short-answer-question.component.html',
    providers: [ShortAnswerQuestionUtil],
    styleUrls: ['./short-answer-question.component.scss', '../../../participate/quiz-participation.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class ShortAnswerQuestionComponent {
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

    // TODO: Map vs. Array --> consistency
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
            this.showSampleSolution();
        }
    }

    get forceSampleSolution() {
        return this._forceSampleSolution;
    }
    @Input()
    fnOnSubmittedTextUpdate: any;

    @Output()
    submittedTextsChange = new EventEmitter<ShortAnswerSubmittedText[]>();

    showingSampleSolution = false;
    renderedQuestion: RenderedQuizQuestionMarkDownElement;
    sampleSolutions: ShortAnswerSolution[] = [];
    textParts: (string | null)[][];

    constructor(private artemisMarkdown: ArtemisMarkdownService, public shortAnswerQuestionUtil: ShortAnswerQuestionUtil) {}

    /**
     * Update html for text, hint and explanation for the question and every answer option
     */
    watchCollection() {
        const artemisMarkdown = this.artemisMarkdown;
        this.renderedQuestion = new RenderedQuizQuestionMarkDownElement();

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
        this.sampleSolutions = this.shortAnswerQuestionUtil.getSampleSolutions(this.question);
        this.showingSampleSolution = true;
    }

    /**
     * Display the student's answer again
     */
    hideSampleSolution() {
        this.showingSampleSolution = false;
    }

    /**
     * Returns the submitted text for a short answer for the given spot tag
     * @param spotTag Spot tag for which to get the submitted text
     */
    getSubmittedTextForSpot(spotTag: string): ShortAnswerSubmittedText {
        return this.submittedTexts.filter((submittedText) => submittedText.spot!.spotNr === this.shortAnswerQuestionUtil.getSpotNr(spotTag))[0];
    }

    /**
     * Returns the submitted text as string for a short answer for the given spot tag
     * @param spotTag Spot tag for which to get the submitted text
     */
    getSubmittedTextForSpotAsString(spotTag: string): string {
        const submittedText = this.getSubmittedTextForSpot(spotTag);
        return submittedText?.text ?? '';
    }

    /**
     * Returns the size for a submitted text for a short answer for the given spot tag
     * @param spotTag Spot tag for which to get the submitted text
     */
    getSubmittedTextSizeForSpot(spotTag: string): number {
        const submittedText = this.getSubmittedTextForSpotAsString(spotTag);
        return submittedText !== '' ? submittedText.length + 2 : 5;
    }

    /**
     * Returns the sample solution for a short answer for the given spot tag
     * @param spotTag Spot tag for which to get the sample solution
     */
    getSampleSolutionForSpot(spotTag: string): ShortAnswerSolution {
        const index = this.question.spots!.findIndex((spot) => spot.spotNr === this.shortAnswerQuestionUtil.getSpotNr(spotTag));
        return this.sampleSolutions[index];
    }

    /**
     * Returns the sample solution as text for a short answer for the given spot tag
     * @param spotTag Spot tag for which to get the sample solution
     */
    getSampleSolutionForSpotAsString(spotTag: string): string {
        const sampleSolution = this.getSampleSolutionForSpot(spotTag);
        return sampleSolution?.text ?? '';
    }

    /**
     * Returns the size for a sample solution for a short answer for the given spot tag
     * @param spotTag Spot tag for which to get the submitted text
     */
    getSampleSolutionSizeForSpot(spotTag: string): number {
        const sampleSolution = this.getSampleSolutionForSpotAsString(spotTag);
        return sampleSolution !== '' ? sampleSolution.length + 2 : 5;
    }

    /**
     * Returns the background color for the input field of the given spot tag
     * @param spotTag Spot tag for which to return the input field's background color
     */
    getBackgroundColourForInputField(spotTag: string): string {
        const submittedTextForSpot = this.getSubmittedTextForSpot(spotTag);
        if (submittedTextForSpot === undefined) {
            return 'red';
        }
        return submittedTextForSpot.isCorrect ? (this.isSubmittedTextCompletelyCorrect(spotTag) ? 'lightgreen' : 'yellow') : 'red';
    }

    /**
     * Returns whether the submitted text for the answer regarding the given spot tag is completely correct
     * @param spotTag Spot tag for which to evaluate
     */
    isSubmittedTextCompletelyCorrect(spotTag: string): boolean {
        let isTextCorrect = false;
        const solutionsForSpot = this.shortAnswerQuestionUtil.getAllSolutionsForSpot(
            this.question.correctMappings,
            this.shortAnswerQuestionUtil.getSpot(this.shortAnswerQuestionUtil.getSpotNr(spotTag), this.question),
        );
        const solutions = solutionsForSpot?.filter((solution) => solution.text?.trim() === this.getSubmittedTextForSpot(spotTag)?.text?.trim());
        if (solutions && solutions.length > 0) {
            isTextCorrect = true;
        }
        return isTextCorrect;
    }
}
