import { Component } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { faBan, faCheck, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { normalWorkingTime } from 'app/exam/participate/exam.utils';

@Component({
    selector: 'jhi-edit-working-time-dialog',
    templateUrl: './exam-edit-working-time-dialog.component.html',
})
export class ExamEditWorkingTimeDialogComponent {
    exam: Exam;

    isLoading: boolean;

    // used by *ngFor in the template
    // Icons
    faBan = faBan;
    faSpinner = faSpinner;
    faCheck = faCheck;

    confirmEntityName: string;

    workingTimeSeconds: number;

    constructor(
        private activeModal: NgbActiveModal,
        private examManagementService: ExamManagementService,
    ) {}

    /**
     * Life cycle hook called by Angular to indicate
     * that Angular is done creating the component
     */
    // ngOnInit(): void {
    //     this.workingTimeSeconds = this.exam.workingTime!;
    // }

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
        if (this.isInvalid()) {
            return;
        }
        this.isLoading = true;
        this.examManagementService.updateWorkingTime(this.exam.course!.id!, this.exam.id!, this.getWorkingTimeChange()).subscribe({
            next: () => {
                // TODO: do we have to inform the application about the change or is it handled by the websocket?
                this.isLoading = false;
                this.clear();
            },
            error: () => {
                this.isLoading = false;
                // TODO: error handling
            },
        });
    }

    isInvalid(): boolean {
        return Math.abs(this.getWorkingTimeChange()) === 0;
    }

    private getWorkingTimeChange(): number {
        const previousWorkingTime = normalWorkingTime(this.exam!)!;
        return this.workingTimeSeconds - previousWorkingTime;
    }
}
