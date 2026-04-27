import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { ExamLiveEventComponent } from 'app/exam/shared/events/exam-live-event.component';
import { Subscription } from 'rxjs';
import {
    ExamLiveEvent,
    ExamLiveEventType,
    ExamParticipationLiveEventsService,
    ProblemStatementUpdateEvent,
} from 'app/exam/overview/services/exam-participation-live-events.service';
import { USER_DISPLAY_RELEVANT_EVENTS, USER_DISPLAY_RELEVANT_EVENTS_REOPEN } from 'app/exam/overview/events/button/exam-live-events-button.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ExamExerciseUpdateService } from 'app/exam/manage/services/exam-exercise-update.service';
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

    readonly unacknowledgedEvents = signal<ExamLiveEvent[]>([]);
    readonly eventsToDisplay = signal<ExamLiveEvent[] | undefined>(undefined);
    readonly events = signal<ExamLiveEvent[]>([]);

    readonly examStartDate = signal<dayjs.Dayjs | undefined>(undefined);
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

        this.newLiveEventsSubscription = this.liveEventsService.observeNewEventsAsUser(USER_DISPLAY_RELEVANT_EVENTS, this.examStartDate()!).subscribe((event: ExamLiveEvent) => {
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
        this.activeModal.close('cancel');
    }

    updateEventsToDisplay() {
        const unacknowledged = this.unacknowledgedEvents();
        this.eventsToDisplay.set(unacknowledged.length > 0 ? unacknowledged : this.events());
    }
}
