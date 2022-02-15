import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { DeleteFileChange, FileType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { IFileDeleteDelegate } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-on-file-delete-delegate';
import { captureException } from '@sentry/browser';
import { faBan, faTrashAlt } from '@fortawesome/free-solid-svg-icons';

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

    // Icons
    faBan = faBan;
    faTrashAlt = faTrashAlt;

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
            this.repositoryFileService.deleteFile(this.fileNameToDelete).subscribe({
                next: () => {
                    this.closeModal();
                    this.parent.onFileDeleted(new DeleteFileChange(this.fileType, this.fileNameToDelete));
                },
                error: (err) => {
                    captureException(err);
                    // TODO: show the error to the user
                },
            });
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
