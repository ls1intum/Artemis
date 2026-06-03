import { Component, OnInit, inject } from '@angular/core';
import { faBan, faExclamationTriangle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ValidationReason } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { JsonPipe } from '@angular/common';

@Component({
    selector: 'jhi-quiz-confirm-import-invalid-questions-modal',
    templateUrl: './quiz-confirm-import-invalid-questions-modal.component.html',
    styleUrls: ['./quiz-confirm-import-invalid-questions-modal.scss'],
    imports: [FormsModule, TranslateDirective, FaIconComponent, JsonPipe],
})
export class QuizConfirmImportInvalidQuestionsModalComponent implements OnInit {
    private dialogRef = inject(DynamicDialogRef);
    private dialogConfig = inject(DynamicDialogConfig);

    // Icons
    faBan = faBan;
    faTimes = faTimes;
    faExclamationTriangle = faExclamationTriangle;

    invalidFlaggedQuestions: ValidationReason[];

    ngOnInit() {
        this.invalidFlaggedQuestions = this.dialogConfig.data.invalidFlaggedQuestions;
    }

    /**
     * Confirm importing the (partially invalid) questions.
     */
    importQuestions() {
        this.dialogRef.close(true);
    }

    closeModal() {
        this.dialogRef.close();
    }
}
