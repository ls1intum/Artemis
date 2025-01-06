import { Component, inject } from '@angular/core';
import { faPause, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-build-agent-pause-all-modal',
    standalone: true,
    imports: [FaIconComponent, TranslateDirective],
    templateUrl: './build-agent-pause-all-modal.component.html',
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

    confirm() {
        this.activeModal.close(true);
    }
}
