import { Component, OnInit } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-quiz-scoring-infostudent-modal',
    templateUrl: './quiz-scoring-infostudent-modal.component.html',
    styles: []
})
export class QuizScoringInfostudentModalComponent implements OnInit {
    constructor(private modalService: NgbModal) {}

    ngOnInit() {}

    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
    }
}
