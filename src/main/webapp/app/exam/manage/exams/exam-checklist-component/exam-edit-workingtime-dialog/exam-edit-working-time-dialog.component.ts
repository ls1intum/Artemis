import { HttpResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { faBan, faCheck, faSpinner } from '@fortawesome/free-solid-svg-icons';

import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { examWorkingTime } from 'app/exam/overview/exam.utils';
import { FormsModule } from '@angular/forms';
import { WorkingTimeChangeComponent } from 'app/exam/shared/working-time-change/working-time-change.component';
import { WorkingTimeControlComponent } from 'app/exam/shared/working-time-control/working-time-control.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ConfirmEntityNameComponent } from 'app/shared-ui/confirm-entity-name/confirm-entity-name.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-edit-working-time-dialog',
    templateUrl: './exam-edit-working-time-dialog.component.html',
    imports: [FormsModule, TranslateDirective, WorkingTimeControlComponent, WorkingTimeChangeComponent, ConfirmEntityNameComponent, FaIconComponent],
})
export class ExamEditWorkingTimeDialogComponent implements OnInit {
    protected readonly faBan = faBan;
    protected readonly faSpinner = faSpinner;
    protected readonly faCheck = faCheck;

    private dialogRef = inject(DynamicDialogRef);
    private dialogConfig = inject(DynamicDialogConfig);
    private examManagementService = inject(ExamManagementService);

    exam = signal<Exam | undefined>(undefined);

    isLoading = false;

    workingTimeSeconds = 0;

    ngOnInit(): void {
        const data = this.dialogConfig?.data;
        if (data?.exam) {
            this.exam.set(data.exam);
        }
    }

    get oldWorkingTime() {
        const currentExam = this.exam();
        return currentExam ? examWorkingTime(currentExam) : undefined;
    }

    get newWorkingTime() {
        return this.oldWorkingTime ? this.oldWorkingTime + this.workingTimeSeconds : undefined;
    }

    clear(): void {
        this.dialogRef.close();
    }

    confirmUpdateWorkingTime(): void {
        if (!this.isWorkingTimeChangeValid) return;
        const currentExam = this.exam();
        if (!currentExam) return;
        this.isLoading = true;
        this.examManagementService.updateWorkingTime(currentExam.course!.id!, currentExam.id!, this.workingTimeSeconds).subscribe({
            next: (res: HttpResponse<Exam>) => {
                this.isLoading = false;
                this.dialogRef.close(res.body ?? undefined);
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
