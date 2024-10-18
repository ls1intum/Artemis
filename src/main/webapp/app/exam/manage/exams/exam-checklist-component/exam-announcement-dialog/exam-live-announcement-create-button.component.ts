import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Component, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { faBullhorn } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Subscription, from } from 'rxjs';

import { Exam } from 'app/entities/exam/exam.model';
import { AlertService } from 'app/core/util/alert.service';
import { ExamLiveAnnouncementCreateModalComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-announcement-dialog/exam-live-announcement-create-modal.component';

@Component({
    selector: 'jhi-exam-live-announcement-create-button',
    templateUrl: './exam-live-announcement-create-button.component.html',
})
export class ExamLiveAnnouncementCreateButtonComponent implements OnInit, OnDestroy {
    private modalService = inject(NgbModal);
    alertService = inject(AlertService);

    @Input() exam: Exam;

    faBullhorn = faBullhorn;
    announcementCreationAllowed = false;

    private modalRef: NgbModalRef | undefined;
    private timeoutRef: any;
    private subscription: Subscription;

    ngOnInit() {
        this.checkAnnouncementCreationAllowed();
    }

    ngOnDestroy() {
        if (this.timeoutRef) {
            clearTimeout(this.timeoutRef);
        }
        if (this.subscription) {
            this.subscription.unsubscribe();
        }
    }

    private checkAnnouncementCreationAllowed() {
        const now = dayjs();

        this.announcementCreationAllowed = !!this.exam.visibleDate?.isBefore(now);

        // Run the check again at the visible date
        if (!this.announcementCreationAllowed) {
            const nextCheckTimeout = this.exam.visibleDate?.diff(now);
            if (nextCheckTimeout) {
                this.timeoutRef = setTimeout(this.checkAnnouncementCreationAllowed.bind(this), nextCheckTimeout);
            }
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

        from(this.modalRef.result).subscribe(() => (this.modalRef = undefined));
    }
}
