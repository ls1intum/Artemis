import { Component, EventEmitter, inject } from '@angular/core';
import { faBan, faExclamationTriangle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ValidationReason } from 'app/exercise/entities/exercise.model';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { JsonPipe } from '@angular/common';

@Component({
    selector: 'jhi-quiz-confirm-import-invalid-questions-modal',
    templateUrl: './quiz-confirm-import-invalid-questions-modal.component.html',
    styleUrls: ['./quiz-confirm-import-invalid-questions-modal.scss'],
    imports: [FormsModule, TranslateDirective, FaIconComponent, JsonPipe],
})
export class QuizConfirmImportInvalidQuestionsModalComponent {
    activeModal = inject(NgbActiveModal);

    // Icons
    faBan = faBan;
    faTimes = faTimes;
    faExclamationTriangle = faExclamationTriangle;

    invalidFlaggedQuestions: ValidationReason[];
    shouldImport: EventEmitter<void> = new EventEmitter<void>();

    /**
     * Reset the git repository.
     *
     * @function resetRepository
     */
    importQuestions() {
        this.activeModal.close();
        this.shouldImport.emit();
    }

    closeModal() {
        this.activeModal.dismiss('cancel');
    }
}
