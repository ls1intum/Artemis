import { ChangeDetectionStrategy, Component, computed, inject, model } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';

/**
 * Modal component for confirming the action to clear all distributed data.
 * Requires the user to type a specific confirmation text ("CLEAR DATA") to enable the confirm button.
 * This additional safety measure prevents accidental data deletion.
 *
 * Uses signals for reactive state management:
 * - `confirmationText`: Two-way bound model for the user's input
 * - `buttonEnabled`: Computed signal that enables the button only when confirmation text matches
 *
 * Uses OnPush change detection for optimal performance.
 */
@Component({
    selector: 'jhi-build-agent-clear-distributed-data',
    imports: [FaIconComponent, TranslateDirective, FormsModule],
    templateUrl: './build-agent-clear-distributed-data.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BuildAgentClearDistributedDataComponent {
    private activeModal = inject(NgbActiveModal);

    /** Two-way bound model for the confirmation text input field */
    confirmationText = model<string>('');

    // Font Awesome icons for the UI
    protected readonly faTimes = faTimes;
    protected readonly faTrash = faTrash;

    /** The exact text the user must type to enable the confirm button */
    private readonly expectedConfirmationText = 'CLEAR DATA';

    /**
     * Computed signal that enables the confirm button only when the user
     * has typed the expected confirmation text exactly.
     */
    buttonEnabled = computed(() => this.confirmationText() === this.expectedConfirmationText);

    /**
     * Closes the modal by dismissing it
     */
    cancel() {
        this.activeModal.dismiss('cancel');
    }

    /**
     * Confirms the clear data action and closes the modal with a positive result.
     */
    confirm() {
        this.activeModal.close(true);
    }
}
