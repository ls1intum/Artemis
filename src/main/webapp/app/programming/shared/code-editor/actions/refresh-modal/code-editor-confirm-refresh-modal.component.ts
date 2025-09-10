import { Component, EventEmitter, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { faBan, faExclamationTriangle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CodeEditorRepositoryFileService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';

@Component({
    selector: 'jhi-code-editor-confirm-refresh-modal',
    templateUrl: './code-editor-confirm-refresh-modal.component.html',
    styleUrls: ['../conflict-modal/code-editor-resolve-conflict-modal.scss'],
    providers: [CodeEditorRepositoryFileService],
    imports: [FormsModule, TranslateDirective, FaIconComponent],
})
export class CodeEditorConfirmRefreshModalComponent {
    private activeModal = inject(NgbActiveModal);

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
