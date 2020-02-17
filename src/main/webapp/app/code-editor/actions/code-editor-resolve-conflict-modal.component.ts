import { Component } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/code-editor/service/code-editor-repository.service';
import { CodeEditorConflictStateService } from 'app/code-editor/service/code-editor-conflict-state.service';
import { GitConflictState } from 'app/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-code-editor-resolve-conflict-modal',
    templateUrl: './code-editor-resolve-conflict-modal.component.html',
    styleUrls: ['./code-editor-resolve-conflict-modal.scss'],
    providers: [CodeEditorRepositoryFileService],
})
export class CodeEditorResolveConflictModalComponent {
    constructor(public activeModal: NgbActiveModal, private repositoryService: CodeEditorRepositoryService, private conflictService: CodeEditorConflictStateService) {}

    /**
     * Reset the git repository to its last commit.
     *
     * @function resetRepository
     */
    resetRepository() {
        this.repositoryService.resetRepository().subscribe(() => {
            this.conflictService.notifyConflictState(GitConflictState.OK);
            this.closeModal();
        });
    }

    closeModal() {
        this.activeModal.dismiss('cancel');
    }
}
