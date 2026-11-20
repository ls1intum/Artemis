import { Component, inject, input, model, output, signal } from '@angular/core';
import { captureException } from 'app/core/error-reporting/capture-exception';
import { faBan, faTrashAlt } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CodeEditorRepositoryFileService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { DeleteFileChange, FileChange, FileType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { TumAetUiButtonDirective, TumAetUiDialogComponent } from '@tumaet/ui-angular';

/**
 * Dialog that asks for confirmation before a file or folder of the repository is deleted.
 */
@Component({
    selector: 'jhi-code-editor-file-browser-delete',
    templateUrl: './code-editor-file-browser-delete.component.html',
    providers: [CodeEditorRepositoryFileService],
    imports: [TranslateDirective, FaIconComponent, ArtemisTranslatePipe, TumAetUiDialogComponent, TumAetUiButtonDirective],
})
export class CodeEditorFileBrowserDeleteComponent {
    private repositoryFileService = inject(CodeEditorRepositoryFileService);

    readonly visible = model(false);
    readonly fileNameToDelete = input<string>();
    readonly fileType = input<FileType>();
    /** Emits the change after the file or folder was deleted in the repository. */
    readonly fileDeleted = output<FileChange>();

    readonly isLoading = signal(false);

    protected readonly FileType = FileType;

    // Icons
    faBan = faBan;
    faTrashAlt = faTrashAlt;

    /**
     * Deletes the file or folder in the repository and reports the change. The dialog closes afterwards, unless it
     * shows the confirmation for another file by then.
     */
    deleteFile() {
        // Guard against PROBLEM_STATEMENT deletion - it's a pseudo-file, not a real repository file
        if (this.fileType() === FileType.PROBLEM_STATEMENT) {
            this.closeModal();
            return;
        }
        const fileName = this.fileNameToDelete();
        const fileType = this.fileType();
        if (!fileName || !fileType) {
            return;
        }
        this.isLoading.set(true);
        this.repositoryFileService.deleteFile(fileName).subscribe({
            next: () => {
                this.isLoading.set(false);
                // The user may have cancelled this confirmation and opened one for another file while the request
                // was running. Only the confirmation that started the request is closed.
                if (this.fileNameToDelete() === fileName) {
                    this.closeModal();
                }
                this.fileDeleted.emit(new DeleteFileChange(fileType, fileName));
            },
            error: (err) => {
                this.isLoading.set(false);
                captureException(err);
                // TODO: show the error to the user
            },
        });
    }

    /**
     * Closes the dialog without deleting anything.
     */
    closeModal() {
        this.visible.set(false);
    }
}
