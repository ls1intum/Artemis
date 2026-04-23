import { Component, OnInit, inject, signal } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { captureException } from '@sentry/angular';
import { faBan, faTrashAlt } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CodeEditorRepositoryFileService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { IFileDeleteDelegate } from 'app/programming/manage/code-editor/file-browser/code-editor-file-browser-on-file-delete-delegate';
import { DeleteFileChange, FileType } from 'app/programming/shared/code-editor/model/code-editor.model';

// Modal -> Delete repository file
@Component({
    selector: 'jhi-code-editor-file-browser-delete',
    templateUrl: './code-editor-file-browser-delete.component.html',
    providers: [CodeEditorRepositoryFileService],
    imports: [FormsModule, TranslateDirective, FaIconComponent, ArtemisTranslatePipe],
})
export class CodeEditorFileBrowserDeleteComponent implements OnInit {
    activeModal = inject(NgbActiveModal);
    private repositoryFileService = inject(CodeEditorRepositoryFileService);

    readonly fileNameToDelete = signal<string | undefined>(undefined);
    readonly parent = signal<IFileDeleteDelegate | undefined>(undefined);
    readonly fileType = signal<FileType | undefined>(undefined);

    isLoading: boolean;

    // Icons
    faBan = faBan;
    faTrashAlt = faTrashAlt;

    /**
     * @function ngOnInit
     * @desc Initializes variables
     */
    ngOnInit(): void {
        this.isLoading = false;
    }

    setInputs({ fileNameToDelete, parent, fileType }: { fileNameToDelete: string; parent: IFileDeleteDelegate; fileType: FileType }) {
        this.fileNameToDelete.set(fileNameToDelete);
        this.parent.set(parent);
        this.fileType.set(fileType);
    }

    /**
     * @function deleteFile
     * @desc Reads the provided fileName and deletes the matching file in the repository
     */
    deleteFile() {
        // Guard against PROBLEM_STATEMENT deletion - it's a pseudo-file, not a real repository file
        if (this.fileType() === FileType.PROBLEM_STATEMENT) {
            this.closeModal();
            return;
        }
        this.isLoading = true;
        // Make sure we have a filename
        const fileName = this.fileNameToDelete();
        const fileType = this.fileType();
        const parent = this.parent();
        if (fileName && fileType && parent) {
            this.repositoryFileService.deleteFile(fileName).subscribe({
                next: () => {
                    this.closeModal();
                    parent.onFileDeleted(new DeleteFileChange(fileType, fileName));
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
