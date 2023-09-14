import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { faHourglassHalf } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Subscription, from } from 'rxjs';

import { Exam } from 'app/entities/exam.model';
import { ExamEditWorkingTimeDialogComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-edit-workingtime-dialog/exam-edit-working-time-dialog.component';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-exam-edit-working-time',
    templateUrl: './exam-edit-working-time.component.html',
})
export class ExamEditWorkingTimeComponent implements OnInit, OnDestroy {
    @Input() exam: Exam;
    @Output() examChange = new EventEmitter<Exam>();

    faHourglassHalf = faHourglassHalf;
    workingTimeChangeAllowed = false;

    private modalRef: NgbModalRef | null;
    private timeoutRef: any;
    private subscription: Subscription;

    constructor(
        private modalService: NgbModal,
        public alertService: AlertService,
    ) {}

    ngOnInit() {
        this.checkWorkingTimeChangeAllowed();
    }

    ngOnDestroy() {
        this.timeoutRef && clearTimeout(this.timeoutRef);
        this.subscription && this.subscription.unsubscribe();
    }

    private checkWorkingTimeChangeAllowed() {
        const endDate = this.exam.endDate?.subtract(1, 'minutes');
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
        this.modalRef = this.modalService.open(ExamEditWorkingTimeDialogComponent, {
            size: 'lg',
            backdrop: 'static',
            animation: true,
        });
        this.modalRef.componentInstance.exam = this.exam;
        this.subscription = this.modalRef.componentInstance.examChange.subscribe((exam: Exam) => this.examChange.emit(exam));

        from(this.modalRef.result).subscribe(() => (this.modalRef = null));
    }
}
