import { Component, Input, inject } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-close-edit-lecture-dialog',
    standalone: true,
    imports: [TranslateDirective, ArtemisSharedCommonModule],
    templateUrl: './close-edit-lecture-dialog.component.html',
})
export class CloseEditLectureDialogComponent {
    protected readonly faTimes = faTimes;

    protected readonly activeModal = inject(NgbActiveModal);

    // not input signals yet as not can not be initialized with current ng-bootstrap version https://stackoverflow.com/a/79094268/16540383
    @Input() hasUnsavedChangesInTitleSection: boolean;
    @Input() hasUnsavedChangesInPeriodSection: boolean;

    closeWindow(isCloseConfirmed: boolean): void {
        this.activeModal.close(isCloseConfirmed);
    }
}
