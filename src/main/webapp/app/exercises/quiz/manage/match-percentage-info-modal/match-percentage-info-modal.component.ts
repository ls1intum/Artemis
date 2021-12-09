import { Component } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-match-percentage-info-modal',
    templateUrl: './match-percentage-info-modal.component.html',
})
export class MatchPercentageInfoModalComponent {
    // Icons
    farQuestionCircle = faQuestionCircle;
    constructor(private modalService: NgbModal) {}

    /**
     * Open a large modal with the given content.
     * @param content the content to display
     */
    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
    }
}
