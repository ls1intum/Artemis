import { Component, EventEmitter } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ValidationReason } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-quiz-confirm-import-invalid-questions-modal',
    templateUrl: './quiz-confirm-import-invalid-questions-modal.component.html',
    styleUrls: ['./quiz-confirm-import-invalid-questions-modal.scss'],
})
export class QuizConfirmImportInvalidQuestionsModalComponent {
    constructor(public activeModal: NgbActiveModal) {}

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
