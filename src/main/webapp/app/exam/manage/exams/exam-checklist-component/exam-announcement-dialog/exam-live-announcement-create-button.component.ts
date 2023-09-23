import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { faBullhorn } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Subscription, from } from 'rxjs';

import { Exam } from 'app/entities/exam.model';
import { AlertService } from 'app/core/util/alert.service';
import { ExamLiveAnnouncementCreateModalComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-announcement-dialog/exam-live-announcement-create-modal.component';

@Component({
    selector: 'jhi-exam-live-announcement-create-button',
    templateUrl: './exam-live-announcement-create-button.component.html',
})
export class ExamLiveAnnouncementCreateButtonComponent implements OnInit, OnDestroy {
    @Input() exam: Exam;

    faBullhorn = faBullhorn;
    announcementCreationAllowed = false;

    private modalRef: NgbModalRef | null;
    private timeoutRef: any;
    private subscription: Subscription;

    constructor(
        private modalService: NgbModal,
        public alertService: AlertService,
    ) {}

    ngOnInit() {
        this.checkAnnouncementCreationAllowed();
    }

    ngOnDestroy() {
        this.timeoutRef && clearTimeout(this.timeoutRef);
        this.subscription && this.subscription.unsubscribe();
    }

    private checkAnnouncementCreationAllowed() {
        const now = dayjs();

        // Exam must be visible ...
        const isVisible = !!this.exam.visibleDate?.isBefore(now);
        // ... and not be completely over, including grace period
        const endWithGracePeriod = this.exam.endDate?.add(this.exam.gracePeriod || 0, 'seconds');
        const isNotOver = !!endWithGracePeriod?.isAfter(now);

        this.announcementCreationAllowed = isVisible && isNotOver;

        // Run the check again at the next relevant time
        let nextCheckTimeout: number | undefined;
        if (isVisible) {
            nextCheckTimeout = this.exam.startDate?.diff(now);
        } else if (isNotOver) {
            nextCheckTimeout = endWithGracePeriod?.diff(now);
        }

        if (nextCheckTimeout) {
            this.timeoutRef = setTimeout(this.checkAnnouncementCreationAllowed.bind(this), nextCheckTimeout);
        }
    }

    openDialog(event: MouseEvent) {
        event.preventDefault();
        this.alertService.closeAll();
        this.modalRef = this.modalService.open(ExamLiveAnnouncementCreateModalComponent, {
            size: 'lg',
            backdrop: 'static',
            animation: true,
        });
        this.modalRef.componentInstance.examId = this.exam.id;
        this.modalRef.componentInstance.courseId = this.exam.course!.id!;

        from(this.modalRef.result).subscribe(() => (this.modalRef = null));
    }
}
