import { Component, OnInit, Input } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-quiz-scoring-infostudent-modal',
    templateUrl: './quiz-scoring-infostudent-modal.component.html',
    styles: []
})
export class QuizScoringInfostudentModalComponent implements OnInit {
    constructor(private modalService: NgbModal) {}

    @Input() score: number;
    @Input() question: number;
    @Input() scoringType: string;
    @Input() selectedOption: boolean;

    @Input() correctAnswers: number;
    @Input() wrongAnswers: number;

    @Input() amountOfAnswerOptions: number;
    @Input() questionType: string;

    ngOnInit() {}

    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
    }
}
