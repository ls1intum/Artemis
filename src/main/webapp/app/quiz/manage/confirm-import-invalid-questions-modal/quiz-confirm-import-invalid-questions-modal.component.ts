import { Component, OnInit, inject, signal } from '@angular/core';
import { faBan, faExclamationTriangle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiDialogConfig, TumAetUiDialogRef } from '@tumaet/ui-angular';
import { ValidationReason } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { JsonPipe } from '@angular/common';

@Component({
    selector: 'jhi-quiz-confirm-import-invalid-questions-modal',
    templateUrl: './quiz-confirm-import-invalid-questions-modal.component.html',
    imports: [FormsModule, TranslateDirective, FaIconComponent, JsonPipe, TumAetUiButtonDirective],
})
export class QuizConfirmImportInvalidQuestionsModalComponent implements OnInit {
    private dialogRef = inject(TumAetUiDialogRef<boolean>);
    private dialogConfig = inject(TumAetUiDialogConfig<{ invalidFlaggedQuestions: ValidationReason[] }>);

    // Icons
    faBan = faBan;
    faTimes = faTimes;
    faExclamationTriangle = faExclamationTriangle;

    readonly invalidFlaggedQuestions = signal<ValidationReason[]>(undefined!);

    ngOnInit() {
        this.invalidFlaggedQuestions.set(this.dialogConfig.data!.invalidFlaggedQuestions);
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
