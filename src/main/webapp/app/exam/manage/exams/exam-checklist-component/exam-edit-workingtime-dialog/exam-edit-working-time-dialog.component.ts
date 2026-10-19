import { HttpResponse } from '@angular/common/http';
import { Component, inject, input, model, output, signal } from '@angular/core';
import { faBan, faCheck, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiDialogComponent } from '@tumaet/ui-angular';

import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { examWorkingTime } from 'app/exam/overview/exam.utils';
import { FormsModule } from '@angular/forms';
import { WorkingTimeChangeComponent } from 'app/exam/shared/working-time-change/working-time-change.component';
import { WorkingTimeControlComponent } from 'app/exam/shared/working-time-control/working-time-control.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ConfirmEntityNameComponent } from 'app/shared-ui/confirm-entity-name/confirm-entity-name.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-edit-working-time-dialog',
    templateUrl: './exam-edit-working-time-dialog.component.html',
    imports: [
        FormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        WorkingTimeControlComponent,
        WorkingTimeChangeComponent,
        ConfirmEntityNameComponent,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiDialogComponent,
    ],
})
export class ExamEditWorkingTimeDialogComponent {
    private examManagementService = inject(ExamManagementService);

    protected readonly faBan = faBan;
    protected readonly faCheck = faCheck;
    protected readonly faSpinner = faSpinner;

    readonly visible = model(true);
    readonly exam = input.required<Exam>();
    readonly workingTimeUpdated = output<Exam>();

    readonly isLoading = signal(false);

    workingTimeSeconds = 0;

    get oldWorkingTime() {
        return examWorkingTime(this.exam());
    }

    get newWorkingTime() {
        return this.oldWorkingTime ? this.oldWorkingTime + this.workingTimeSeconds : undefined;
    }

    clear(): void {
        this.visible.set(false);
    }

    confirmUpdateWorkingTime(): void {
        if (!this.isWorkingTimeChangeValid) return;
        const currentExam = this.exam();
        this.isLoading.set(true);
        this.examManagementService.updateWorkingTime(currentExam.course!.id!, currentExam.id!, this.workingTimeSeconds).subscribe({
            next: (res: HttpResponse<Exam>) => {
                this.isLoading.set(false);
                if (res.body) {
                    this.workingTimeUpdated.emit(res.body);
                }
                this.visible.set(false);
            },
            error: () => {
                // If an error happens, the alert service takes care of displaying an error message
                this.isLoading.set(false);
            },
        });
    }

    get isWorkingTimeChangeValid(): boolean {
        return Math.abs(this.workingTimeSeconds) !== 0;
    }
}
