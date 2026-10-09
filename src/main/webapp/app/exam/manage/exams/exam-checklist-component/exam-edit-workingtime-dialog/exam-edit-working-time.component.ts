import { Component, OnDestroy, OnInit, inject, input, output, signal } from '@angular/core';
import { faHourglassHalf } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';

import { Exam } from 'app/exam/shared/entities/exam.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { ExamEditWorkingTimeDialogComponent } from './exam-edit-working-time-dialog.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-exam-edit-working-time',
    templateUrl: './exam-edit-working-time.component.html',
    imports: [FaIconComponent, TranslateDirective, TumAetUiButtonDirective, ExamEditWorkingTimeDialogComponent],
})
export class ExamEditWorkingTimeComponent implements OnInit, OnDestroy {
    alertService = inject(AlertService);

    exam = input.required<Exam>();
    examChange = output<Exam>();

    faHourglassHalf = faHourglassHalf;
    readonly workingTimeChangeAllowed = signal(false);

    readonly dialogVisible = signal(false);
    private timeoutRef: ReturnType<typeof setTimeout> | undefined;

    ngOnInit() {
        this.checkWorkingTimeChangeAllowed();
    }

    ngOnDestroy() {
        if (this.timeoutRef) {
            clearTimeout(this.timeoutRef);
        }
    }

    private checkWorkingTimeChangeAllowed() {
        const endDate = this.exam().endDate?.subtract(1, 'minutes');
        this.workingTimeChangeAllowed.set(dayjs().isBefore(endDate));

        // Run the check again when the exam ends
        const nextCheckTimeout = endDate?.diff();
        if (nextCheckTimeout) {
            this.timeoutRef = setTimeout(this.checkWorkingTimeChangeAllowed.bind(this), nextCheckTimeout);
        }
    }

    openDialog(event: MouseEvent) {
        event.preventDefault();
        this.alertService.closeAll();
        this.dialogVisible.set(true);
    }
}
