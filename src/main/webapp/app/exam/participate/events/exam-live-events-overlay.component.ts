import { Component, OnDestroy, OnInit } from '@angular/core';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { Subscription } from 'rxjs';
import { ExamLiveEvent, ExamLiveEventType, ExamParticipationLiveEventsService } from 'app/exam/participate/exam-participation-live-events.service';
import { USER_DISPLAY_RELEVANT_EVENTS, USER_DISPLAY_RELEVANT_EVENTS_REOPEN } from 'app/exam/participate/events/exam-live-events-button.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-exam-live-events-overlay',
    templateUrl: './exam-live-events-overlay.component.html',
    styleUrls: ['./exam-live-events-overlay.component.scss'],
})
export class ExamLiveEventsOverlayComponent implements OnInit, OnDestroy {
    private allLiveEventsSubscription?: Subscription;
    private newLiveEventsSubscription?: Subscription;

    unacknowledgedEvents: ExamLiveEvent[] = [];
    eventsToDisplay?: ExamLiveEvent[];
    events: ExamLiveEvent[] = [];

    // Icons
    faCheck = faCheck;

    protected readonly ExamLiveEventType = ExamLiveEventType;

    constructor(
        private liveEventsService: ExamParticipationLiveEventsService,
        private activeModal: NgbActiveModal,
    ) {}

    ngOnDestroy(): void {
        this.allLiveEventsSubscription?.unsubscribe();
        this.newLiveEventsSubscription?.unsubscribe();
    }

    ngOnInit(): void {
        this.allLiveEventsSubscription = this.liveEventsService.observeAllEvents(USER_DISPLAY_RELEVANT_EVENTS_REOPEN).subscribe((events: ExamLiveEvent[]) => {
            this.events = events;
            if (!this.eventsToDisplay) {
                this.updateEventsToDisplay();
            }
        });

        this.newLiveEventsSubscription = this.liveEventsService.observeNewEventsAsUser(USER_DISPLAY_RELEVANT_EVENTS).subscribe((event: ExamLiveEvent) => {
            this.unacknowledgedEvents.unshift(event);
            this.updateEventsToDisplay();
        });
    }

    acknowledgeEvent(event: ExamLiveEvent) {
        this.liveEventsService.acknowledgeEvent(event, true);
        this.unacknowledgedEvents = this.unacknowledgedEvents.filter((e) => e.id !== event.id);
        if (this.unacknowledgedEvents.length === 0) {
            this.closeOverlay();
            setTimeout(() => this.updateEventsToDisplay(), 250);
        } else {
            this.updateEventsToDisplay();
        }
    }

    acknowledgeAllUnacknowledgedEvents() {
        this.unacknowledgedEvents.forEach((event) => this.liveEventsService.acknowledgeEvent(event, true));
        this.unacknowledgedEvents = [];
        this.closeOverlay();
        setTimeout(() => this.updateEventsToDisplay(), 250);
    }

    closeOverlay() {
        this.activeModal.close('cancel');
    }

    updateEventsToDisplay() {
        this.eventsToDisplay = this.unacknowledgedEvents.length > 0 ? this.unacknowledgedEvents : this.events;
    }
}
