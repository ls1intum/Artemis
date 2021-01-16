import { Component, OnInit } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-match-percentage-info-modal',
    templateUrl: './match-percentage-info-modal.component.html',
})
export class MatchPercentageInfoModalComponent implements OnInit {
    constructor(private modalService: NgbModal) {}

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
