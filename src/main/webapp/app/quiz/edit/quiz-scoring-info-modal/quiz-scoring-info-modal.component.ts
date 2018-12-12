import { Component, OnInit } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-quiz-scoring-info-modal',
    templateUrl: './quiz-scoring-info-modal.component.html',
    styles: []
})
export class QuizScoringInfoModalComponent implements OnInit {
    constructor(private modalService: NgbModal) {}

    ngOnInit() {}

    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
    }
}
