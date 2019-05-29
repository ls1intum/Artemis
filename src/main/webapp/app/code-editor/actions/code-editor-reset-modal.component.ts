import { Component, Input, Output, EventEmitter } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { RepositoryService } from 'app/entities/repository';
import { Participation } from 'app/entities/participation';
import { CodeEditorComponent } from 'app/code-editor';

@Component({
    selector: 'jhi-code-editor-reset-modal',
    templateUrl: './code-editor-reset-modal.component.html',
})
export class CodeEditorResetModalComponent {
    @Input() participation: Participation;
    @Input() parent: CodeEditorComponent;

    isLoading: boolean;

    constructor(public activeModal: NgbActiveModal, private repositoryService: RepositoryService) {}

    resetRepository() {
        this.repositoryService.reset(this.participation.id).subscribe(
            () => {
                this.closeModal();
                this.parent.onResetSuccess();
            },
            () => {
                this.parent.onError('resetFailed');
            },
        );
    }

    /**
     * @function closeModal
     * @desc Dismisses the currently active modal (popup)
     */
    closeModal() {
        this.activeModal.dismiss('cancel');
    }
}
