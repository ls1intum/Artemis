import { Component } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { faBan, faCheck, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { Exam } from 'app/entities/exam.model';
import { EntityResponseType, ExamManagementService } from 'app/exam/manage/exam-management.service';

@Component({
    selector: 'jhi-edit-working-time-dialog',
    templateUrl: './exam-edit-working-time-dialog.component.html',
})
export class ExamEditWorkingTimeDialogComponent {
    exam: Exam;
    examChange?: (exam: Exam) => void; // somehow, event emitter does not work

    isLoading: boolean;

    // used by *ngFor in the template
    // Icons
    faBan = faBan;
    faSpinner = faSpinner;
    faCheck = faCheck;

    confirmEntityName: string;

    workingTimeSeconds = 0;

    constructor(
        private activeModal: NgbActiveModal,
        private examManagementService: ExamManagementService,
    ) {}

    /**
     * Closes the dialog
     */
    clear(): void {
        // intentionally use close instead of dismiss here, because dismiss leads to a non-traceable runtime error
        this.activeModal.close();
    }

    /**
     * Emits delete event and passes additional checks from the dialog
     */
    confirmUpdateWorkingTime(): void {
        if (!this.isWorkingTimeValid()) return;
        this.isLoading = true;
        this.examManagementService.updateWorkingTime(this.exam.course!.id!, this.exam.id!, this.workingTimeSeconds).subscribe({
            next: (res: EntityResponseType) => {
                this.isLoading = false;
                res.body && this.examChange?.(res.body);
                this.clear();
            },
            error: () => {
                this.isLoading = false;
                // TODO: error handling
            },
        });
    }

    isWorkingTimeValid(): boolean {
        return Math.abs(this.workingTimeSeconds) !== 0;
    }
}
