import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { faPause, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DynamicDialogRef } from 'primeng/dynamicdialog';

/**
 * Modal component for confirming the action to pause all build agents.
 * Provides a simple confirmation dialog with cancel and confirm buttons.
 *
 * Uses OnPush change detection for optimal performance.
 */
@Component({
    selector: 'jhi-build-agent-pause-all-modal',
    imports: [FaIconComponent, TranslateDirective],
    templateUrl: './build-agent-pause-all-modal.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BuildAgentPauseAllModalComponent {
    private readonly dialogRef = inject(DynamicDialogRef);

    protected readonly faTimes = faTimes;
    protected readonly faPause = faPause;

    /**
     * Closes the modal without confirming the action.
     */
    cancel() {
        this.dialogRef.close();
    }

    /**
     * Confirms the pause action and closes the modal with a positive result.
     */
    confirm() {
        this.dialogRef.close(true);
    }
}
