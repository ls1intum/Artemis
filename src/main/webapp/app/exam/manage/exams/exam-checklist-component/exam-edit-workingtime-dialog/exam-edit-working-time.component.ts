import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { Component, OnDestroy, OnInit, inject, input, output } from '@angular/core';
import { faHourglassHalf } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Subscription } from 'rxjs';

import { Exam } from 'app/exam/shared/entities/exam.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { ExamEditWorkingTimeDialogComponent } from './exam-edit-working-time-dialog.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-exam-edit-working-time',
    templateUrl: './exam-edit-working-time.component.html',
    imports: [FaIconComponent, TranslateDirective],
})
export class ExamEditWorkingTimeComponent implements OnInit, OnDestroy {
    private dialogService = inject(DialogService);
    private translateService = inject(TranslateService);
    alertService = inject(AlertService);

    exam = input.required<Exam>();
    examChange = output<Exam>();

    faHourglassHalf = faHourglassHalf;
    workingTimeChangeAllowed = false;

    private dialogRef: DynamicDialogRef | null | undefined;
    private timeoutRef: any;
    private subscription: Subscription | undefined;

    ngOnInit() {
        this.checkWorkingTimeChangeAllowed();
    }

    ngOnDestroy() {
        if (this.timeoutRef) {
            clearTimeout(this.timeoutRef);
        }
        this.subscription?.unsubscribe();
    }

    private checkWorkingTimeChangeAllowed() {
        const endDate = this.exam().endDate?.subtract(1, 'minutes');
        this.workingTimeChangeAllowed = dayjs().isBefore(endDate);

        // Run the check again when the exam ends
        const nextCheckTimeout = endDate?.diff();
        if (nextCheckTimeout) {
            this.timeoutRef = setTimeout(this.checkWorkingTimeChangeAllowed.bind(this), nextCheckTimeout);
        }
    }

    openDialog(event: MouseEvent) {
        event.preventDefault();
        this.alertService.closeAll();
        this.dialogRef = this.dialogService.open(ExamEditWorkingTimeDialogComponent, {
            header: this.translateService.instant('artemisApp.examManagement.editWorkingTime.title'),
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            data: {
                exam: this.exam(),
            },
        });

        this.subscription = this.dialogRef?.onClose.subscribe((updatedExam: Exam | undefined) => {
            if (updatedExam) {
                this.examChange.emit(updatedExam);
            }
            this.dialogRef = undefined;
        });
    }
}
