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
    @Input() selectedOption: boolean;

    @Input() correctlyChosenAnswers: number;
    @Input() correctAnswer: number;
    @Input() wronglyChosenAnswers: number;

    @Input() amountOfAnswerOptions: number;

    @Input() question: Question;

    forgottenRightAnswers: number;

    constructor(private modalService: NgbModal) {
    }

    ngOnInit() {
        this.count()
    }

    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
    }

    count() {
        this.forgottenRightAnswers = this.correctAnswer - this.correctlyChosenAnswers;
    }


}
