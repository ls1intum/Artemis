import { Component, EventEmitter, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeEditorConflictStateService } from 'app/exercises/programming/shared/code-editor/service/code-editor-conflict-state.service';
import { faBan, faExclamationTriangle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-code-editor-confirm-refresh-modal',
    templateUrl: './code-editor-confirm-refresh-modal.component.html',
    styleUrls: ['./code-editor-resolve-conflict-modal.scss'],
    providers: [CodeEditorRepositoryFileService],
    imports: [FormsModule, TranslateDirective, FaIconComponent],
})
export class CodeEditorConfirmRefreshModalComponent {
    activeModal = inject(NgbActiveModal);
    private repositoryService = inject(CodeEditorRepositoryService);
    private conflictService = inject(CodeEditorConflictStateService);

    // Icons
    faExclamationTriangle = faExclamationTriangle;
    faBan = faBan;
    faTimes = faTimes;

    shouldRefresh: EventEmitter<void> = new EventEmitter<void>();

    /**
     * Reset the git repository.
     *
     * @function resetRepository
     */
    refreshFiles() {
        this.activeModal.close();
        this.shouldRefresh.emit();
    }

    closeModal() {
        this.activeModal.dismiss('cancel');
    }
}
