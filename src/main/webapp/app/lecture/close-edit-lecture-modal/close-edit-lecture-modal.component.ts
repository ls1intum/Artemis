import { Component, Input, inject } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faTimes } from '@fortawesome/free-solid-svg-icons';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-close-edit-lecture-modal',
    imports: [TranslateDirective, FontAwesomeModule],
    templateUrl: './close-edit-lecture-modal.component.html',
})
export class CloseEditLectureModalComponent {
    protected readonly faTimes = faTimes;

    protected readonly activeModal = inject(NgbActiveModal);

    // no input signals yet as they can not be initialized with current ng-bootstrap version https://stackoverflow.com/a/79094268/16540383
    @Input() hasUnsavedChangesInTitleSection: boolean;
    @Input() hasUnsavedChangesInPeriodSection: boolean;

    closeWindow(isCloseConfirmed: boolean): void {
        this.activeModal.close(isCloseConfirmed);
    }
}
