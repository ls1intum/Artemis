import { Component, Input, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { faBullhorn } from '@fortawesome/free-solid-svg-icons';
import { AlertService } from 'app/core/util/alert.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Subscription, from } from 'rxjs';
import { ExamLiveEvent, ExamLiveEventType, ExamParticipationLiveEventsService } from 'app/exam/participate/exam-participation-live-events.service';
import { ExamLiveEventsOverlayComponent } from 'app/exam/participate/events/exam-live-events-overlay.component';
import dayjs from 'dayjs/esm';

export const USER_DISPLAY_RELEVANT_EVENTS = [
    ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT,
    ExamLiveEventType.WORKING_TIME_UPDATE,
    ExamLiveEventType.EXAM_ATTENDANCE_CHECK,
    ExamLiveEventType.PROBLEM_STATEMENT_UPDATE,
];
export const USER_DISPLAY_RELEVANT_EVENTS_REOPEN = [ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT, ExamLiveEventType.WORKING_TIME_UPDATE, ExamLiveEventType.PROBLEM_STATEMENT_UPDATE];

@Component({
    selector: 'jhi-exam-live-events-button',
    templateUrl: './exam-live-events-button.component.html',
    styleUrls: ['./exam-live-events-button.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class ExamLiveEventsButtonComponent implements OnInit, OnDestroy {
    private modalRef?: NgbModalRef;
    private liveEventsSubscription?: Subscription;
    private allEventsSubscription?: Subscription;
    eventCount = 0;
    @Input() examStartDate: dayjs.Dayjs;

    // Icons
    faBullhorn = faBullhorn;

    constructor(
        private alertService: AlertService,
        private modalService: NgbModal,
        private liveEventsService: ExamParticipationLiveEventsService,
    ) {}

    ngOnInit(): void {
        this.allEventsSubscription = this.liveEventsService.observeAllEvents(USER_DISPLAY_RELEVANT_EVENTS_REOPEN).subscribe((events: ExamLiveEvent[]) => {
            // do not count the problem statements events that are made before the start of the exam
            const filteredEvents = events.filter((event) => !(event.eventType === ExamLiveEventType.PROBLEM_STATEMENT_UPDATE && event.createdDate.isBefore(this.examStartDate)));
            this.eventCount = filteredEvents.length;
        });

        this.liveEventsSubscription = this.liveEventsService.observeNewEventsAsUser(USER_DISPLAY_RELEVANT_EVENTS, this.examStartDate).subscribe(() => {
            // If any unacknowledged event comes in, open the dialog to display it
            if (!this.modalRef) {
                this.openDialog();
            }
        });
    }

    ngOnDestroy(): void {
        this.liveEventsSubscription?.unsubscribe();
        this.allEventsSubscription?.unsubscribe();
    }

    openDialog(event?: MouseEvent) {
        event?.preventDefault();

        this.alertService.closeAll();
        this.modalRef = this.modalService.open(ExamLiveEventsOverlayComponent, {
            size: 'lg',
            backdrop: 'static',
            animation: false,
            centered: true,
            windowClass: 'live-events-modal-window',
        });

        this.modalRef.componentInstance.examStartDate = this.examStartDate;

        from(this.modalRef.result).subscribe(() => (this.modalRef = undefined));
    }
}
