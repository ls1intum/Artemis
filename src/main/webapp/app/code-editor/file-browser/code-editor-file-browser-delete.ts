import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { DeleteFileChange, FileType } from 'app/code-editor/model/code-editor.model';
import { CodeEditorRepositoryFileService } from 'app/code-editor/service/code-editor-repository.service';
import { IFileDeleteDelegate } from 'app/code-editor/file-browser/code-editor-file-browser-on-file-delete-delegate';

// Modal -> Delete repository file
@Component({
    selector: 'jhi-code-editor-file-browser-delete',
    templateUrl: './code-editor-file-browser-delete.html',
    providers: [CodeEditorRepositoryFileService],
})
export class CodeEditorFileBrowserDeleteComponent implements OnInit {
    @Input() fileNameToDelete: string;
    @Input() parent: IFileDeleteDelegate;
    @Input() fileType: FileType;

    isLoading: boolean;

    constructor(public activeModal: NgbActiveModal, private repositoryFileService: CodeEditorRepositoryFileService) {}

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
            this.repositoryFileService.deleteFile(this.fileNameToDelete).subscribe(
                () => {
                    this.closeModal();
                    this.parent.onFileDeleted(new DeleteFileChange(this.fileType, this.fileNameToDelete));
                },
                err => {
                    console.log('Error deleting file: ' + this.fileNameToDelete, err);
                },
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
