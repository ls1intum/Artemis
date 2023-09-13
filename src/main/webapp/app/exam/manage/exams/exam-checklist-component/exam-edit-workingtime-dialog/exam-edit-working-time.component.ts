import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { faHourglassHalf } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { from } from 'rxjs';

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
    private intervalRef: any;

    constructor(
        private modalService: NgbModal,
        public alertService: AlertService,
    ) {}

    ngOnInit() {
        this.checkWorkingTimeChangeAllowed();
        this.intervalRef = setInterval(this.checkWorkingTimeChangeAllowed.bind(this), 1000);
    }

    ngOnDestroy() {
        this.intervalRef && clearInterval(this.intervalRef);
    }

    private checkWorkingTimeChangeAllowed() {
        this.workingTimeChangeAllowed = dayjs().isBetween(this.exam.startDate, this.exam.endDate?.subtract(5, 'minutes'));
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
        this.modalRef.componentInstance.examChange.subscribe((exam: Exam) => this.examChange.emit(exam));

        from(this.modalRef.result).subscribe(() => (this.modalRef = null));
    }
}
