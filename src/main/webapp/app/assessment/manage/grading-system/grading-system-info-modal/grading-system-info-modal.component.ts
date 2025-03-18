import { Component, inject } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-grading-system-info-modal',
    templateUrl: './grading-system-info-modal.component.html',
    imports: [TranslateDirective, FaIconComponent],
})
export class GradingSystemInfoModalComponent {
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
