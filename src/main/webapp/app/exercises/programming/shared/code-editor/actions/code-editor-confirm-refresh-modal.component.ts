import { Component, EventEmitter, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { faBan, faExclamationTriangle, faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-code-editor-confirm-refresh-modal',
    templateUrl: './code-editor-confirm-refresh-modal.component.html',
    styleUrls: ['./code-editor-resolve-conflict-modal.scss'],
    providers: [CodeEditorRepositoryFileService],
})
export class CodeEditorConfirmRefreshModalComponent {
    activeModal = inject(NgbActiveModal);

    // Icons
    faExclamationTriangle = faExclamationTriangle;
    faBan = faBan;
    faTimes = faTimes;

    shouldRefresh: EventEmitter<void> = new EventEmitter<void>();

    /**
     * Reset the git repository.
     *
     * @function resetRepository
     */
    refreshFiles() {
        this.activeModal.close();
        this.shouldRefresh.emit();
    }

    closeModal() {
        this.activeModal.dismiss('cancel');
    }
}
