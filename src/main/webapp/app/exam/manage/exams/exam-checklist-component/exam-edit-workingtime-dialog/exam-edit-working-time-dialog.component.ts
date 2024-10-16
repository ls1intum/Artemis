import { HttpResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { faBan, faCheck, faSpinner } from '@fortawesome/free-solid-svg-icons';

import { Exam } from 'app/entities/exam/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { examWorkingTime } from 'app/exam/participate/exam.utils';

@Component({
    selector: 'jhi-edit-working-time-dialog',
    templateUrl: './exam-edit-working-time-dialog.component.html',
})
export class ExamEditWorkingTimeDialogComponent {
    private activeModal = inject(NgbActiveModal);
    private examManagementService = inject(ExamManagementService);

    @Input() exam: Exam;
    @Output() examChange = new EventEmitter<Exam>();

    isLoading: boolean;

    // Icons
    faBan = faBan;
    faSpinner = faSpinner;
    faCheck = faCheck;

    workingTimeSeconds = 0;

    get oldWorkingTime() {
        return examWorkingTime(this.exam);
    }

    get newWorkingTime() {
        return this.oldWorkingTime ? this.oldWorkingTime + this.workingTimeSeconds : undefined;
    }

    clear(): void {
        this.activeModal.close();
    }

    confirmUpdateWorkingTime(): void {
        if (!this.isWorkingTimeChangeValid) return;
        this.isLoading = true;
        this.examManagementService.updateWorkingTime(this.exam.course!.id!, this.exam.id!, this.workingTimeSeconds).subscribe({
            next: (res: HttpResponse<Exam>) => {
                this.isLoading = false;
                if (res.body) {
                    this.examChange.emit(res.body);
                }
                this.clear();
            },
            error: () => {
                // If an error happens, the alert service takes care of displaying an error message
                this.isLoading = false;
            },
        });
    }

    get isWorkingTimeChangeValid(): boolean {
        return Math.abs(this.workingTimeSeconds) !== 0;
    }
}
