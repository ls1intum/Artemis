import { Component, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { ExamLiveEventComponent } from 'app/exam/shared/events/exam-live-event.component';
import { Subscription } from 'rxjs';
import { ExamLiveEvent, ExamLiveEventType, ExamParticipationLiveEventsService, ProblemStatementUpdateEvent } from 'app/exam/participate/exam-participation-live-events.service';
import { USER_DISPLAY_RELEVANT_EVENTS, USER_DISPLAY_RELEVANT_EVENTS_REOPEN } from 'app/exam/participate/events/exam-live-events-button.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ExamExerciseUpdateService } from 'app/exam/manage/exam-exercise-update.service';
import dayjs from 'dayjs/esm';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-exam-live-events-overlay',
    templateUrl: './exam-live-events-overlay.component.html',
    styleUrls: ['./exam-live-events-overlay.component.scss'],
    imports: [ExamLiveEventComponent, FaIconComponent, TranslateDirective],
})
export class ExamLiveEventsOverlayComponent implements OnInit, OnDestroy {
    private liveEventsService = inject(ExamParticipationLiveEventsService);
    private activeModal = inject(NgbActiveModal);
    private examExerciseUpdateService = inject(ExamExerciseUpdateService);

    private allLiveEventsSubscription?: Subscription;
    private newLiveEventsSubscription?: Subscription;

    unacknowledgedEvents: ExamLiveEvent[] = [];
    eventsToDisplay?: ExamLiveEvent[];
    events: ExamLiveEvent[] = [];

    @Input() examStartDate: dayjs.Dayjs;
    // Icons
    faCheck = faCheck;

    protected readonly ExamLiveEventType = ExamLiveEventType;

    ngOnDestroy(): void {
        this.allLiveEventsSubscription?.unsubscribe();
        this.newLiveEventsSubscription?.unsubscribe();
    }

    ngOnInit(): void {
        this.allLiveEventsSubscription = this.liveEventsService.observeAllEvents(USER_DISPLAY_RELEVANT_EVENTS_REOPEN).subscribe((events: ExamLiveEvent[]) => {
            // display the problem statements events only after the start of the exam
            this.events = events.filter((event) => !(event.eventType === ExamLiveEventType.PROBLEM_STATEMENT_UPDATE && event.createdDate.isBefore(this.examStartDate)));
            if (!this.eventsToDisplay) {
                this.updateEventsToDisplay();
            }
        });

        this.newLiveEventsSubscription = this.liveEventsService.observeNewEventsAsUser(USER_DISPLAY_RELEVANT_EVENTS, this.examStartDate).subscribe((event: ExamLiveEvent) => {
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

    navigateToExercise(event: ExamLiveEvent) {
        this.acknowledgeEvent(event);
        const problemStatementUpdateEvent = event as ProblemStatementUpdateEvent;
        const exerciseId = problemStatementUpdateEvent.exerciseId;
        this.examExerciseUpdateService.navigateToExamExercise(exerciseId);
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
