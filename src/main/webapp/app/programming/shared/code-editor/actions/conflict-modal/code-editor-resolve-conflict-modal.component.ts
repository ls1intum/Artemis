import { Component, inject } from '@angular/core';
import { faBan } from '@fortawesome/free-solid-svg-icons/faBan';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons/faExclamationTriangle';
import { faTimes } from '@fortawesome/free-solid-svg-icons/faTimes';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiDialogRef } from '@tumaet/ui-angular/dialog';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-code-editor-resolve-conflict-modal',
    templateUrl: './code-editor-resolve-conflict-modal.component.html',
    imports: [FormsModule, TranslateDirective, FaIconComponent, TumAetUiButtonDirective],
})
export class CodeEditorResolveConflictModalComponent {
    private readonly dialogRef = inject<TumAetUiDialogRef<boolean>>(TumAetUiDialogRef);

    // Icons
    faBan = faBan;
    faTimes = faTimes;
    faExclamationTriangle = faExclamationTriangle;

    /**
     * Reset the git repository to its last commit.
     *
     * @function resetRepository
     */
    resetRepository() {
        this.dialogRef.close(true);
    }

    closeModal() {
        this.dialogRef.close();
    }
}
