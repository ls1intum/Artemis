import { Component, OnDestroy, OnInit, ViewEncapsulation, inject, input, signal } from '@angular/core';
import { faBullhorn } from '@fortawesome/free-solid-svg-icons';
import { AlertService } from 'app/shared/service/alert.service';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { ExamLiveEvent, ExamLiveEventType, ExamParticipationLiveEventsService } from 'app/exam/overview/services/exam-participation-live-events.service';
import { ExamLiveEventsOverlayComponent } from 'app/exam/overview/events/overlay/exam-live-events-overlay.component';
import dayjs from 'dayjs/esm';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

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
    imports: [FaIconComponent],
})
export class ExamLiveEventsButtonComponent implements OnInit, OnDestroy {
    private alertService = inject(AlertService);
    private dialogService = inject(DialogService);
    private translateService = inject(TranslateService);
    private liveEventsService = inject(ExamParticipationLiveEventsService);

    private dialogRef: DynamicDialogRef | null | undefined;
    private liveEventsSubscription?: Subscription;
    private allEventsSubscription?: Subscription;
    readonly eventCount = signal(0);
    readonly examStartDate = input<dayjs.Dayjs>(undefined!);

    // Icons
    faBullhorn = faBullhorn;

    ngOnInit(): void {
        this.allEventsSubscription = this.liveEventsService.observeAllEvents(USER_DISPLAY_RELEVANT_EVENTS_REOPEN).subscribe((events: ExamLiveEvent[]) => {
            // do not count the problem statements events that are made before the start of the exam
            const filteredEvents = events.filter((event) => !(event.eventType === ExamLiveEventType.PROBLEM_STATEMENT_UPDATE && event.createdDate.isBefore(this.examStartDate())));
            this.eventCount.set(filteredEvents.length);
        });

        this.liveEventsSubscription = this.liveEventsService.observeNewEventsAsUser(USER_DISPLAY_RELEVANT_EVENTS, this.examStartDate()).subscribe(() => {
            // If any unacknowledged event comes in, open the dialog to display it
            if (!this.dialogRef) {
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
        this.dialogRef = this.dialogService.open(ExamLiveEventsOverlayComponent, {
            header: this.translateService.instant('artemisApp.exam.events.title'),
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: false,
            dismissableMask: false,
            styleClass: 'live-events-modal-window',
            data: {
                examStartDate: this.examStartDate(),
            },
        });

        this.dialogRef?.onClose.subscribe(() => (this.dialogRef = undefined));
    }
}
