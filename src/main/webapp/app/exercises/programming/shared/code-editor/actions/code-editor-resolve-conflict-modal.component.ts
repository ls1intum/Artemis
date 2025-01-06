import { Component, EventEmitter, inject } from '@angular/core';
import { faBan, faExclamationTriangle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
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
    activeModal = inject(NgbActiveModal);

    // Icons
    faBan = faBan;
    faTimes = faTimes;
    faExclamationTriangle = faExclamationTriangle;

    shouldReset: EventEmitter<void> = new EventEmitter<void>();

    /**
     * Reset the git repository to its last commit.
     *
     * @function resetRepository
     */
    resetRepository() {
        this.activeModal.close();
        this.shouldReset.emit();
    }

    closeModal() {
        this.activeModal.dismiss('cancel');
    }
}
