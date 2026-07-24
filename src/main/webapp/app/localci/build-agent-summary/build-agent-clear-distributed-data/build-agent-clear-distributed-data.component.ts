import { ChangeDetectionStrategy, Component, computed, effect, model, output, untracked } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { TumUiDialogComponent } from 'app/shared-ui/tum-ui/dialog/tum-ui-dialog.component';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';
import { TumUiInputDirective } from 'app/shared-ui/tum-ui/input/tum-ui-input.directive';

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
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe, FormsModule, TumUiDialogComponent, TumUiButtonComponent, TumUiInputDirective],
    templateUrl: './build-agent-clear-distributed-data.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BuildAgentClearDistributedDataComponent {
    /** Two-way visibility of the dialog, driven by the parent. */
    readonly visible = model<boolean>(false);

    /** Emitted when the user confirms clearing the distributed data. */
    readonly confirmed = output<void>();

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

    constructor() {
        // This modal instance persists across opens (its host is always in the parent's DOM), so reset the
        // confirmation text whenever the dialog is (re)opened to avoid a pre-filled, already-enabled confirm button.
        effect(() => {
            if (this.visible()) {
                untracked(() => this.confirmationText.set(''));
            }
        });
    }

    /**
     * Closes the modal without confirming the action.
     */
    cancel() {
        this.visible.set(false);
    }

    /**
     * Confirms the clear data action and closes the modal.
     */
    confirm() {
        this.confirmed.emit();
        this.visible.set(false);
    }
}
