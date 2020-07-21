import { Component, EventEmitter } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeEditorConflictStateService } from 'app/exercises/programming/shared/code-editor/service/code-editor-conflict-state.service';
import { GitConflictState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

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
