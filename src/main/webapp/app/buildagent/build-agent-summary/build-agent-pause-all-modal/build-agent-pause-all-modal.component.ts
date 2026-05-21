import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { faPause, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

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
    private activeModal = inject(NgbActiveModal);

    protected readonly faTimes = faTimes;
    protected readonly faPause = faPause;

    /**
     * Closes the modal by dismissing it
     */
    cancel() {
        this.activeModal.dismiss('cancel');
    }

    /**
     * Confirms the pause action and closes the modal with a positive result.
     */
    confirm() {
        this.activeModal.close(true);
    }
}
