import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { RepositoryFileService } from 'app/entities/repository';
import { Participation } from 'app/entities/participation';
import { CodeEditorFileBrowserComponent } from 'app/code-editor';

// Modal -> Delete repository file
@Component({
    selector: 'jhi-code-editor-file-browser-delete',
    templateUrl: './code-editor-file-browser-delete.html'
})
export class CodeEditorFileBrowserDeleteComponent implements OnInit {
    @Input() participation: Participation;
    @Input() fileNameToDelete: string;
    @Input() parent: CodeEditorFileBrowserComponent;

    isLoading: boolean;

    constructor(public activeModal: NgbActiveModal, private repositoryFileService: RepositoryFileService) {}

    /**
     * @function ngOnInit
     * @desc Initializes variables
     */
    ngOnInit(): void {
        this.isLoading = false;
    }

    /**
     * @function deleteFile
     * @desc Reads the provided fileName and deletes the matching file in the repository
     */
    deleteFile() {
        this.isLoading = true;
        // Make sure we have a filename
        if (this.fileNameToDelete) {
            this.repositoryFileService.delete(this.participation.id, this.fileNameToDelete).subscribe(
                () => {
                    this.closeModal();
                    this.parent.getRepositoryFiles();
                    this.parent.onDeletedFile({ file: this.fileNameToDelete, mode: 'delete' });
                },
                err => {
                    console.log('Error deleting file: ' + this.fileNameToDelete, err);
                }
            );
        }
        this.isLoading = false;
    }

    /**
     * @function closeModal
     * @desc Dismisses the currently active modal (popup)
     */
    closeModal() {
        this.activeModal.dismiss('cancel');
    }
}
