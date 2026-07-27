import { ChangeDetectionStrategy, Component, model, output } from '@angular/core';
import { faPause, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiDialogComponent } from 'app/shared-ui/tum-ui/dialog/tum-ui-dialog.component';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';

/**
 * Modal component for confirming the action to pause all build agents.
 * Provides a simple confirmation dialog with cancel and confirm buttons.
 *
 * Uses OnPush change detection for optimal performance.
 */
@Component({
    selector: 'jhi-build-agent-pause-all-modal',
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe, TumUiDialogComponent, TumUiButtonComponent],
    templateUrl: './build-agent-pause-all-modal.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BuildAgentPauseAllModalComponent {
    /** Two-way visibility of the dialog, driven by the parent. */
    readonly visible = model<boolean>(false);

    /** Emitted when the user confirms pausing all build agents. */
    readonly confirmed = output<void>();

    protected readonly faTimes = faTimes;
    protected readonly faPause = faPause;

    /**
     * Closes the modal without confirming the action.
     */
    cancel() {
        this.visible.set(false);
    }

    /**
     * Confirms the pause action and closes the modal.
     */
    confirm() {
        this.confirmed.emit();
        this.visible.set(false);
    }
}
