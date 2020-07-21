import { Component, EventEmitter } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-code-editor-resolve-conflict-modal',
    templateUrl: './code-editor-resolve-conflict-modal.component.html',
    styleUrls: ['./code-editor-resolve-conflict-modal.scss'],
})
export class CodeEditorResolveConflictModalComponent {
    constructor(public activeModal: NgbActiveModal) {}

    shouldReset: EventEmitter<void> = new EventEmitter<void>();

    /**
     * Reset the git repository to its last commit.
     *
     * @function resetRepository
     */
    resetRepository() {
        this.activeModal.close();
        this.shouldReset.emit();
    }

    closeModal() {
        this.activeModal.dismiss('cancel');
    }
}
