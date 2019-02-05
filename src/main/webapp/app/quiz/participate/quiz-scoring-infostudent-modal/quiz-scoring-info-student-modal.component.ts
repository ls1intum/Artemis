import { Component, OnInit, Input } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Question, QuestionType, ScoringType } from 'app/entities/question';

@Component({
    selector: 'jhi-quiz-scoring-infostudent-modal',
    templateUrl: './quiz-scoring-info-student-modal.component.html',
    styles: []
})
export class QuizScoringInfoStudentModalComponent implements OnInit {
    readonly DRAG_AND_DROP = QuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuestionType.MULTIPLE_CHOICE;

    readonly ALL_OR_NOTHING = ScoringType.ALL_OR_NOTHING;
    readonly PROPORTIONAL_WITH_PENALTY = ScoringType.PROPORTIONAL_WITH_PENALTY;

    @Input() score: number;

    @Input() question: Question;
    @Input() DragAndDropMapping = new Array<DragAndDropMapping>();
    @Input() MultipleChoiceMapping = new Array<AnswerOption>();

    @Input() correctAnswer: number;

    /* Multiple Choice Counting Variables*/
    multipleChoiceCorrectAnswerCorrectlyChosen: number;
    multipleChoiceWrongAnswerChosen: number;
    forgottenRightAnswers: number;
    amountOfAnswerOptions: number;
    dragAndDropItemsOnTheRightLocation: number;

    /* Multiple Choice Counting Variables*/
    dragAndDropElementsCount: number;

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

        }
    }

    open(content: any) {
        this.modalService.open(content, {size: 'lg'});

    }

    private countMultipleChoice() {
        const mcmQuestion = this.question as MultipleChoiceQuestion;
            this.amountOfAnswerOptions = mcmQuestion.answerOptions.length;
            this.multipleChoiceCorrectAnswerCorrectlyChosen = this.MultipleChoiceMapping.filter(option => option.isCorrect).length; // how many right answers chosen correctly
            this.multipleChoiceWrongAnswerChosen = this.MultipleChoiceMapping.filter(option => !option.isCorrect).length; // how many wrong answers have been selected
            this.forgottenRightAnswers = this.MultipleChoiceMapping.filter(option => option.isCorrect).length - this.multipleChoiceCorrectAnswerCorrectlyChosen; // how many right options have been forgotten to be chosen
    }

    private countDragandDrop() {
        const dndQuestion = this.question as DragAndDropQuestion;
        this.dragAndDropElementsCount = dndQuestion.dropLocations.length;

    }

}
