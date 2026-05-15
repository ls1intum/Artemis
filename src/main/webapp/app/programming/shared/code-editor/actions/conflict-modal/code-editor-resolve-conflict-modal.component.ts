import { Component, inject } from '@angular/core';
import { faBan, faExclamationTriangle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-code-editor-resolve-conflict-modal',
    templateUrl: './code-editor-resolve-conflict-modal.component.html',
    styleUrls: ['./code-editor-resolve-conflict-modal.scss'],
    imports: [FormsModule, TranslateDirective, FaIconComponent],
})
export class CodeEditorResolveConflictModalComponent {
    private readonly dialogRef = inject(DynamicDialogRef);

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
