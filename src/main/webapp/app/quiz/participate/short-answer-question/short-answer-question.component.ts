import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { ShortAnswerQuestion } from '../../../entities/short-answer-question';
import { ShortAnswerSolution } from '../../../entities/short-answer-solution';
import { ShortAnswerSubmittedText } from 'app/entities/short-answer-submitted-text';

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
    textWithOutSpotsFirstParts: string[];
    textWithOutSpotsLastPart: string[];
    sampleSolutions: ShortAnswerSolution[] =  [];
    isList = false;
    firstLineHasQuestion = false;

    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    ngOnInit() {}

    ngOnDestroy() {}

    watchCollection() {
        // update html for text, hint and explanation for the question and every answer option
        const artemisMarkdown = this.artemisMarkdown;
        this.rendered = new ShortAnswerQuestion();

        //first line is the question if there is no [-spot #] tag in the string
        if(this.question.text.split(/\n/g)[0].search(/\[-spot/g) == -1){
            this.questionText = this.question.text.split(/\n/g)[0];
            this.firstLineHasQuestion = true;
        } else {
            this.questionText = "";
        }

        let questionTextSplitAtNewLine = "";
        //seperates the the rest of the text from the question
        if(this.firstLineHasQuestion){
            questionTextSplitAtNewLine = this.question.text
                .split(/\n+/g)
                .slice(1)
                .join();
        } else {
            questionTextSplitAtNewLine = this.question.text
                .split(/\n+/g)
                .join();
        }

        //checks if a line break is in the text (marked by "," and replaces it) and check if text is a list
        if(questionTextSplitAtNewLine.includes(",")){
            questionTextSplitAtNewLine = questionTextSplitAtNewLine.replace(/\,/g, " ");
            if(questionTextSplitAtNewLine.includes("1.")){
                this.isList = true;
            }
        }

        //splits the text at the "[-spot " tag to have the parts of the text without spot tag
        this.textWithoutSpots = questionTextSplitAtNewLine.split(/\[-spot\s\d\]/g);
        //separates the text into parts that come before the spot tag
        this.textWithOutSpotsFirstParts = this.textWithoutSpots.slice(0, this.textWithoutSpots.length - 1);
        //the last part that comes after the last spot tag
        this.textWithOutSpotsLastPart = this.textWithoutSpots.slice(this.textWithoutSpots.length - 1);

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
        for (let id in this.textWithOutSpotsFirstParts) {
            let submittedText = new ShortAnswerSubmittedText();
            submittedText.text = (<HTMLInputElement>document.getElementById('solution-' + id)).value;
            submittedText.spot = this.question.spots[id];
            this.submittedTexts.push(submittedText);
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
        this.sampleSolutions = [];
        for(let spot of this.question.spots){
            for(let mapping of this.question.correctMappings){
                if(mapping.spot.id  === spot.id
                    && !this.sampleSolutions.some(sampleSolution =>
                        sampleSolution.text  === mapping.solution.text )){
                    this.sampleSolutions.push(mapping.solution);
                    break;
                }
            }
        }
        this.showingSampleSolution = true;
    }

    /**
     * Display the student's answer again
     */
    hideSampleSolution() {
        this.showingSampleSolution = false;
    }
}
