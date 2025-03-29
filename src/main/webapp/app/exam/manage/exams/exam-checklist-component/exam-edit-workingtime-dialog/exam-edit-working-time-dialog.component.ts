import { HttpResponse } from '@angular/common/http';
import { Component, inject, output } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { faBan, faCheck, faSpinner } from '@fortawesome/free-solid-svg-icons';

import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { examWorkingTime } from 'app/exam/overview/exam.utils';
import { FormsModule } from '@angular/forms';
import { WorkingTimeChangeComponent } from 'app/exam/shared/working-time-change/working-time-change.component';
import { WorkingTimeControlComponent } from 'app/exam/shared/working-time-control/working-time-control.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ConfirmEntityNameComponent } from 'app/shared/confirm-entity-name/confirm-entity-name.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-edit-working-time-dialog',
    templateUrl: './exam-edit-working-time-dialog.component.html',
    imports: [FormsModule, TranslateDirective, WorkingTimeControlComponent, WorkingTimeChangeComponent, ConfirmEntityNameComponent, FaIconComponent],
})
export class ExamEditWorkingTimeDialogComponent {
    private activeModal = inject(NgbActiveModal);
    private examManagementService = inject(ExamManagementService);

    exam: Exam;
    examChange = output<Exam>();

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
