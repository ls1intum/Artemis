import { Component, OnInit, Input } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-quiz-scoring-info-modal',
    templateUrl: './quiz-scoring-info-modal.component.html',
})
export class QuizScoringInfoModalComponent implements OnInit {
    // Icons
    farQuestionCircle = faQuestionCircle;
    constructor(private modalService: NgbModal) {}

    @Input()
    hasSingleChoice = false;

    /**
     * Do nothing on init.
     */
    ngOnInit() {}

    /**
     * Open a large modal with the given content.
     * @param content the content to display
     */
    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
    }
}
