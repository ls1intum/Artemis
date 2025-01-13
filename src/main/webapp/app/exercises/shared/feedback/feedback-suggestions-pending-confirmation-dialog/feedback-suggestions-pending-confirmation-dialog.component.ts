import { Component, inject } from '@angular/core';
import { faBan, faTimes } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-feedback-suggestions-pending-confirmation-dialog',
    templateUrl: './feedback-suggestions-pending-confirmation-dialog.component.html',
    imports: [FormsModule, TranslateDirective, FaIconComponent, NgClass],
})
export class FeedbackSuggestionsPendingConfirmationDialogComponent {
    private activeModal = inject(NgbActiveModal);

    // Icons
    faBan = faBan;
    faTimes = faTimes;

    /**
     * Close the confirmation dialog
     */
    close(confirm: boolean): void {
        this.activeModal.close(confirm);
    }
}
