import { Component, OnInit, Input } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Question, QuestionType, ScoringType } from 'app/entities/question';
import { DragAndDropMapping } from '../../../entities/drag-and-drop-mapping';
import { AnswerOption } from '../../../entities/answer-option';
import { MultipleChoiceQuestion } from '../../../entities/multiple-choice-question';
import { DragAndDropQuestion } from '../../../entities/drag-and-drop-question';
import { ShortAnswerQuestion } from '../../../entities/short-answer-question';
import { ShortAnswerSubmittedText } from 'app/entities/short-answer-submitted-text';

@Component({
    selector: 'jhi-quiz-scoring-infostudent-modal',
    templateUrl: './quiz-scoring-info-student-modal.component.html',
    styles: []
})
export class QuizScoringInfoStudentModalComponent implements OnInit {
    readonly DRAG_AND_DROP = QuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuestionType.SHORT_ANSWER;

    readonly ALL_OR_NOTHING = ScoringType.ALL_OR_NOTHING;
    readonly PROPORTIONAL_WITH_PENALTY = ScoringType.PROPORTIONAL_WITH_PENALTY;

    @Input() score: number; // Score of the student that has been achieved
    @Input() question: Question;
    @Input() DragAndDropMapping = new Array<DragAndDropMapping>();
    @Input() MultipleChoiceMapping = new Array<AnswerOption>();
    @Input() ShortAnswerText = new Array<ShortAnswerSubmittedText>();

    /* Multiple Choice Counting Variables*/
    multipleChoiceCorrectAnswerCorrectlyChosen: number;
    multipleChoiceWrongAnswerChosen: number;
    amountofCorrectMultipleChoiceAnswers:number;
    forgottenMultipleChoiceRightAnswers: number;
    amountOfAnswerOptions: number;
    inTotalRightOptions: number;
    inTotalWrongOptions: number;
    differenceMultipleChoice: number;

    /* Drag and Drop Counting Variables*/
    dragAndDropElementsCount: number;
    wrongMappedItems: number;
    @Input() correctlyMappedDragandDropItems: number; //Amount of correctly mapped drag and drop items
    differenceDragandDrop: number;

    /* Short Answer Counting Variables*/
    shortAnswerSpotCount: number;
    shortAnswerCorrectAnswers: number;
    shortAnswerWrongOption: number;
    differenceShortAnswer: number;


    constructor(private modalService: NgbModal) {
    }

    ngOnInit() {
        switch (this.question.type) {
            case QuestionType.MULTIPLE_CHOICE:
                this.countMultipleChoice();
                break;
            case QuestionType.DRAG_AND_DROP:
                this.countDragandDrop();
                break;
            case QuestionType.SHORT_ANSWER:
                this.countShortAnswer();
                break;
        }
    }

    open(content: any) {
        this.modalService.open(content, {size: 'lg'});

    }

    private countMultipleChoice() {
        const mcmQuestion = this.question as MultipleChoiceQuestion;
            this.amountOfAnswerOptions = mcmQuestion.answerOptions.length; // Amount of answer options
            this.amountofCorrectMultipleChoiceAnswers = mcmQuestion.answerOptions.filter(option => option.isCorrect).length;
            this.multipleChoiceCorrectAnswerCorrectlyChosen = this.MultipleChoiceMapping.filter(option => option.isCorrect).length; // how many right answers chosen correctly
            this.multipleChoiceWrongAnswerChosen = this.MultipleChoiceMapping.filter(option => !option.isCorrect).length; // how many wrong answers have been selected
            this.forgottenMultipleChoiceRightAnswers = this.amountofCorrectMultipleChoiceAnswers - this.multipleChoiceCorrectAnswerCorrectlyChosen; // how many right options have been forgotten to be chosen
            this.inTotalRightOptions = this.multipleChoiceCorrectAnswerCorrectlyChosen + (this.amountOfAnswerOptions - this.amountofCorrectMultipleChoiceAnswers-this.multipleChoiceWrongAnswerChosen)
            this.inTotalWrongOptions = this.multipleChoiceWrongAnswerChosen + this.forgottenMultipleChoiceRightAnswers;
            this.differenceMultipleChoice = this.inTotalRightOptions - this.inTotalWrongOptions;
    }

    private countDragandDrop() {
        const dndQuestion = this.question as DragAndDropQuestion;
        this.dragAndDropElementsCount = dndQuestion.dropLocations.length; //Amount of drag and drop zones that should be matched
        this.wrongMappedItems = this.dragAndDropElementsCount - this.correctlyMappedDragandDropItems; //Amount of wrong matched items or not matched at all
        this.differenceDragandDrop = this.correctlyMappedDragandDropItems - this.wrongMappedItems;
    }

    private countShortAnswer() {
        const shortAnswer = this.question as ShortAnswerQuestion;
        this.shortAnswerSpotCount = shortAnswer.spots.length; //Amount of short zones that should be answered
        this.shortAnswerCorrectAnswers = this.ShortAnswerText.filter(option => option.isCorrect).length;//Amount of right answers
        this.shortAnswerWrongOption = this.shortAnswerSpotCount - this.shortAnswerCorrectAnswers;
        this.differenceShortAnswer = this.shortAnswerCorrectAnswers - this.shortAnswerWrongOption;
    }

}
