import { Component } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeEditorConflictStateService } from 'app/exercises/programming/shared/code-editor/service/code-editor-conflict-state.service';
import { GitConflictState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-code-editor-confirm-refresh-modal',
    templateUrl: './code-editor-confirm-refresh-modal.component.html',
    styleUrls: ['./code-editor-resolve-conflict-modal.scss'],
    providers: [CodeEditorRepositoryFileService],
})
export class CodeEditorConfirmRefreshModalComponent {
    constructor(public activeModal: NgbActiveModal, private repositoryService: CodeEditorRepositoryService, private conflictService: CodeEditorConflictStateService) {}

    /**
     * Reset the git repository.
     *
     * @function resetRepository
     */
    refreshFiles() {
        this.repositoryService.resetRepository().subscribe(() => {
            this.conflictService.notifyConflictState(GitConflictState.REFRESH);
            this.closeModal();
        });
    }

    closeModal() {
        this.activeModal.dismiss('cancel');
    }
}
