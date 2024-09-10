import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { faHourglassHalf } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Subscription, from } from 'rxjs';

import { Exam } from 'app/entities/exam/exam.model';
import { AlertService } from 'app/core/util/alert.service';
import { ExamEditWorkingTimeDialogComponent } from './exam-edit-working-time-dialog.component';

@Component({
    selector: 'jhi-exam-edit-working-time',
    templateUrl: './exam-edit-working-time.component.html',
})
export class ExamEditWorkingTimeComponent implements OnInit, OnDestroy {
    @Input() exam: Exam;
    @Output() examChange = new EventEmitter<Exam>();

    faHourglassHalf = faHourglassHalf;
    workingTimeChangeAllowed = false;

    private modalRef: NgbModalRef | undefined;
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
        if (this.timeoutRef) {
            clearTimeout(this.timeoutRef);
        }
        if (this.subscription) {
            this.subscription.unsubscribe();
        }
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

        from(this.modalRef.result).subscribe(() => (this.modalRef = undefined));
    }
}
