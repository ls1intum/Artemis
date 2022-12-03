import { Component, EventEmitter, Input, Output, ViewEncapsulation } from '@angular/core';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { QuizQuestion, RenderedQuizQuestionMarkDownElement } from 'app/entities/quiz/quiz-question.model';
import { faExclamationCircle } from '@fortawesome/free-solid-svg-icons';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { MAX_QUIZ_SHORT_ANSWER_TEXT_LENGTH } from 'app/shared/constants/input.constants';

@Component({
    selector: 'jhi-short-answer-question',
    templateUrl: './short-answer-question.component.html',
    providers: [ShortAnswerQuestionUtil],
    styleUrls: ['./short-answer-question.component.scss', '../../../participate/quiz-participation.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class ShortAnswerQuestionComponent {
    shortAnswerQuestion: ShortAnswerQuestion;
    _forceSampleSolution: boolean;

    @Input()
    set question(question: QuizQuestion) {
        this.shortAnswerQuestion = question as ShortAnswerQuestion;
        this.watchCollection();
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

    readonly maxCharacterCount = MAX_QUIZ_SHORT_ANSWER_TEXT_LENGTH;

    showingSampleSolution = false;
    renderedQuestion: RenderedQuizQuestionMarkDownElement;
    sampleSolutions: ShortAnswerSolution[] = [];
    textParts: string[][];

    // Icons
    faExclamationCircle = faExclamationCircle;
    farQuestionCircle = faQuestionCircle;

    constructor(private artemisMarkdown: ArtemisMarkdownService, public shortAnswerQuestionUtil: ShortAnswerQuestionUtil) {}

    /**
     * Update html for text, hint and explanation for the question and every answer option
     */
    watchCollection() {
        this.renderedQuestion = new RenderedQuizQuestionMarkDownElement();

        const textParts = this.shortAnswerQuestionUtil.divideQuestionTextIntoTextParts(this.shortAnswerQuestion.text!);
        this.textParts = this.shortAnswerQuestionUtil.transformTextPartsIntoHTML(textParts);

        this.renderedQuestion.text = this.artemisMarkdown.safeHtmlForMarkdown(this.shortAnswerQuestion.text);
        this.renderedQuestion.hint = this.artemisMarkdown.safeHtmlForMarkdown(this.shortAnswerQuestion.hint);
        this.renderedQuestion.explanation = this.artemisMarkdown.safeHtmlForMarkdown(this.shortAnswerQuestion.explanation);
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
                    submittedText.text = (<HTMLInputElement>document.getElementById('solution-' + i + '-' + j + '-' + this.shortAnswerQuestion.id)).value;
                    submittedText.spot = this.shortAnswerQuestionUtil.getSpot(this.shortAnswerQuestionUtil.getSpotNr(element!), this.shortAnswerQuestion);
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
        this.sampleSolutions = this.shortAnswerQuestionUtil.getSampleSolutions(this.shortAnswerQuestion);
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
        const index = this.shortAnswerQuestion.spots!.findIndex((spot) => spot.spotNr === this.shortAnswerQuestionUtil.getSpotNr(spotTag));
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
     * Returns the text that should be shown for the given spot tag
     * @param spotTag Spot tag for which to get the text
     */
    getTextForSpotAsString(spotTag: string): string {
        if (this.showingSampleSolution) {
            return this.getSampleSolutionForSpotAsString(spotTag);
        }
        return this.getSubmittedTextForSpotAsString(spotTag);
    }

    /**
     * Returns the size of the input for the given spot tag
     * @param spotTag Spot tag for which to get the size
     */
    getSizeForSpot(spotTag: string): number {
        if (this.showingSampleSolution) {
            return this.getSampleSolutionSizeForSpot(spotTag);
        }
        return this.getSubmittedTextSizeForSpot(spotTag);
    }

    /**
     * Returns the class for the input field of the given spot tag
     * @param spotTag Spot tag for which to return the input field's class
     */
    classifyInputField(spotTag: string): string {
        if (this.shortAnswerQuestion.invalid) {
            return 'invalid';
        }
        const spot = this.shortAnswerQuestionUtil.getSpot(this.shortAnswerQuestionUtil.getSpotNr(spotTag), this.shortAnswerQuestion);
        if (spot.invalid) {
            return 'invalid';
        }
        if (this.showingSampleSolution) {
            return 'completely-correct';
        }
        const submittedTextForSpot = this.getSubmittedTextForSpot(spotTag);
        if (submittedTextForSpot?.isCorrect !== true) {
            return 'wrong';
        }
        if (this.isSubmittedTextCompletelyCorrect(spotTag)) {
            return 'completely-correct';
        }
        return 'correct';
    }

    /**
     * Returns whether the submitted text for the answer regarding the given spot tag is completely correct
     * @param spotTag Spot tag for which to evaluate
     */
    isSubmittedTextCompletelyCorrect(spotTag: string): boolean {
        let isTextCorrect = false;
        const solutionsForSpot = this.shortAnswerQuestionUtil.getAllSolutionsForSpot(
            this.shortAnswerQuestion.correctMappings,
            this.shortAnswerQuestionUtil.getSpot(this.shortAnswerQuestionUtil.getSpotNr(spotTag), this.shortAnswerQuestion),
        );
        const solutions = solutionsForSpot?.filter((solution) => solution.text?.trim() === this.getSubmittedTextForSpot(spotTag)?.text?.trim());
        if (solutions && solutions.length > 0) {
            isTextCorrect = true;
        }
        return isTextCorrect;
    }
}
