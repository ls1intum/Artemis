import { Component, Input, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ButtonComponent } from 'app/shared/components/button/button.component';

@Component({
    selector: 'jhi-unsaved-changes-warning',
    templateUrl: './unsaved-changes-warning.component.html',
    imports: [FormsModule, TranslateDirective, ButtonComponent],
})
export class UnsavedChangesWarningComponent {
    private activeModal = inject(NgbActiveModal);

    @Input() textMessage: string;

    /**
     * Closes the modal in which the warning is shown and discards the changes
     *
     */
    discardContent() {
        this.activeModal.close();
    }

    /**
     * Closes the modal in which the warning is shown
     */
    continueEditing() {
        this.activeModal.dismiss('cancel');
    }
}
