import { Component, inject } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-match-percentage-info-modal',
    templateUrl: './match-percentage-info-modal.component.html',
})
export class MatchPercentageInfoModalComponent {
    private modalService = inject(NgbModal);

    // Icons
    farQuestionCircle = faQuestionCircle;

    /**
     * Open a large modal with the given content.
     * @param content the content to display
     */
    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
    }
}
