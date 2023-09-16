import { Component, EventEmitter, Input, Output } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { faBan, faCheck, faSpinner } from '@fortawesome/free-solid-svg-icons';

import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { normalWorkingTime } from 'app/exam/participate/exam.utils';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-edit-working-time-dialog',
    templateUrl: './exam-edit-working-time-dialog.component.html',
})
export class ExamEditWorkingTimeDialogComponent {
    @Input() exam: Exam;
    @Output() examChange = new EventEmitter<Exam>();

    isLoading: boolean;

    // Icons
    faBan = faBan;
    faSpinner = faSpinner;
    faCheck = faCheck;

    confirmEntityName: string;

    workingTimeSeconds = 0;

    get absoluteWorkingTimeDuration() {
        const currentWorkingTimeSeconds = normalWorkingTime(this.exam);
        if (!currentWorkingTimeSeconds) return undefined;
        const duration = dayjs.duration(currentWorkingTimeSeconds + this.workingTimeSeconds, 'seconds');
        return [duration.asHours() >= 1 ? `${Math.floor(duration.asHours())} h` : null, duration.format('m [min] s [s]')].filter(Boolean).join(' ');
    }

    constructor(
        private activeModal: NgbActiveModal,
        private examManagementService: ExamManagementService,
    ) {}

    clear(): void {
        this.activeModal.close();
    }

    confirmUpdateWorkingTime(): void {
        if (!this.isWorkingTimeValid()) return;
        this.isLoading = true;
        this.examManagementService.updateWorkingTime(this.exam.course!.id!, this.exam.id!, this.workingTimeSeconds).subscribe({
            next: (res: HttpResponse<Exam>) => {
                this.isLoading = false;
                res.body && this.examChange.emit(res.body);
                this.clear();
            },
            error: () => {
                // If an error happens, the alert service takes care of displaying an error message
                this.isLoading = false;
            },
        });
    }

    isWorkingTimeValid(): boolean {
        return Math.abs(this.workingTimeSeconds) !== 0;
    }
}
