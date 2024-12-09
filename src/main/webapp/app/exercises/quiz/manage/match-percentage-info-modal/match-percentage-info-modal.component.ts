import { Component } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-match-percentage-info-modal',
    templateUrl: './match-percentage-info-modal.component.html',
    standalone: true,
    imports: [TranslateDirective, FaIconComponent],
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
