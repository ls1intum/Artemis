import { Component, computed, inject, model } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';

@Component({
    selector: 'jhi-build-agent-clear-distributed-data',
    standalone: true,
    imports: [FaIconComponent, TranslateDirective, FormsModule],
    templateUrl: './build-agent-clear-distributed-data.component.html',
})
export class BuildAgentClearDistributedDataComponent {
    private activeModal = inject(NgbActiveModal);

    confirmationText = model<string>('');

    protected readonly faTimes = faTimes;
    protected readonly faTrash = faTrash;

    private readonly expectedConfirmationText = 'CLEAR DATA';

    buttonEnabled = computed(() => this.confirmationText() === this.expectedConfirmationText);

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
