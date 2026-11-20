import { Component, inject } from '@angular/core';
import { TumAetUiButtonDirective, TumAetUiDialogRef } from '@tumaet/ui-angular';
import { faBan, faExclamationTriangle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CodeEditorRepositoryFileService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';

@Component({
    selector: 'jhi-code-editor-confirm-refresh-modal',
    templateUrl: './code-editor-confirm-refresh-modal.component.html',
    providers: [CodeEditorRepositoryFileService],
    imports: [FormsModule, TranslateDirective, FaIconComponent, TumAetUiButtonDirective],
})
export class CodeEditorConfirmRefreshModalComponent {
    private readonly dialogRef = inject<TumAetUiDialogRef<boolean>>(TumAetUiDialogRef);

    // Icons
    faExclamationTriangle = faExclamationTriangle;
    faBan = faBan;
    faTimes = faTimes;

    /**
     * Reset the git repository.
     *
     * @function refreshFiles
     */
    refreshFiles() {
        this.dialogRef.close(true);
    }

    closeModal() {
        this.dialogRef.close();
    }
}
