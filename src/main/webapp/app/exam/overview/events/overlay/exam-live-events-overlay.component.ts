import { Component, OnDestroy, OnInit, inject, input, output, signal } from '@angular/core';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { ExamLiveEventComponent } from 'app/exam/shared/events/exam-live-event.component';
import { Subscription } from 'rxjs';
import {
    ExamLiveEvent,
    ExamLiveEventType,
    ExamParticipationLiveEventsService,
    ProblemStatementUpdateEvent,
} from 'app/exam/overview/services/exam-participation-live-events.service';
import { USER_DISPLAY_RELEVANT_EVENTS, USER_DISPLAY_RELEVANT_EVENTS_REOPEN } from 'app/exam/overview/events/exam-live-events.constants';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { ExamExerciseUpdateService } from 'app/exam/manage/services/exam-exercise-update.service';
import dayjs from 'dayjs/esm';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-exam-live-events-overlay',
    templateUrl: './exam-live-events-overlay.component.html',
    imports: [ExamLiveEventComponent, FaIconComponent, TranslateDirective, TumAetUiButtonDirective],
})
export class ExamLiveEventsOverlayComponent implements OnInit, OnDestroy {
    private liveEventsService = inject(ExamParticipationLiveEventsService);
    private examExerciseUpdateService = inject(ExamExerciseUpdateService);

    private allLiveEventsSubscription?: Subscription;
    private newLiveEventsSubscription?: Subscription;

    readonly unacknowledgedEvents = signal<ExamLiveEvent[]>([]);
    readonly eventsToDisplay = signal<ExamLiveEvent[] | undefined>(undefined);
    readonly events = signal<ExamLiveEvent[]>([]);

    /** The start date of the exam. Read live, so that a postponed start date is reflected in the filter. */
    readonly examStartDate = input<dayjs.Dayjs | undefined>();
    /** Emitted when the overlay wants its dialog to close. */
    readonly closed = output<void>();
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
            this.events.set(events.filter((event) => !(event.eventType === ExamLiveEventType.PROBLEM_STATEMENT_UPDATE && event.createdDate.isBefore(this.examStartDate()))));
            if (!this.eventsToDisplay()) {
                this.updateEventsToDisplay();
            }
        });

        // Pass the signal itself (not a snapshot) so the pre-start filter honours a live start-date change.
        this.newLiveEventsSubscription = this.liveEventsService.observeNewEventsAsUser(USER_DISPLAY_RELEVANT_EVENTS, this.examStartDate).subscribe((event: ExamLiveEvent) => {
            this.unacknowledgedEvents.update((events) => [event, ...events]);
            this.updateEventsToDisplay();
        });
    }

    acknowledgeEvent(event: ExamLiveEvent) {
        this.liveEventsService.acknowledgeEvent(event, true);
        this.unacknowledgedEvents.update((events) => events.filter((e) => e.id !== event.id));
        if (this.unacknowledgedEvents().length === 0) {
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
        this.unacknowledgedEvents().forEach((event) => this.liveEventsService.acknowledgeEvent(event, true));
        this.unacknowledgedEvents.set([]);
        this.closeOverlay();
        setTimeout(() => this.updateEventsToDisplay(), 250);
    }

    closeOverlay() {
        this.closed.emit();
    }

    updateEventsToDisplay() {
        const unacknowledged = this.unacknowledgedEvents();
        this.eventsToDisplay.set(unacknowledged.length > 0 ? unacknowledged : this.events());
    }
}
