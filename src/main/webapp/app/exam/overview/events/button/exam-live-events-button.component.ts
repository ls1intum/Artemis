import { Component, OnDestroy, OnInit, inject, input, signal } from '@angular/core';
import { faBullhorn } from '@fortawesome/free-solid-svg-icons';
import { AlertService } from 'app/foundation/service/alert.service';
import { TumAetUiButtonDirective, TumAetUiDialogComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
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
    imports: [FaIconComponent, TumAetUiButtonDirective, TumAetUiDialogComponent, ExamLiveEventsOverlayComponent, ArtemisTranslatePipe],
})
export class ExamLiveEventsButtonComponent implements OnInit, OnDestroy {
    private alertService = inject(AlertService);
    private liveEventsService = inject(ExamParticipationLiveEventsService);

    private liveEventsSubscription?: Subscription;
    private allEventsSubscription?: Subscription;
    readonly dialogVisible = signal(false);
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

        // Pass the signal itself (not a snapshot) so the pre-start filter honours a live start-date change.
        this.liveEventsSubscription = this.liveEventsService.observeNewEventsAsUser(USER_DISPLAY_RELEVANT_EVENTS, this.examStartDate).subscribe(() => {
            // If any unacknowledged event comes in, open the dialog to display it
            if (!this.dialogVisible()) {
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
        this.dialogVisible.set(true);
    }
}
