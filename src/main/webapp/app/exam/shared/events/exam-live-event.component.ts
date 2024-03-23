import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faCheck, faEye, faPaperPlane } from '@fortawesome/free-solid-svg-icons';
import {
    ExamAttendanceCheckEvent,
    ExamLiveEvent,
    ExamLiveEventType,
    ExamWideAnnouncementEvent,
    ProblemStatementUpdateEvent,
    WorkingTimeUpdateEvent,
} from 'app/exam/participate/exam-participation-live-events.service';

@Component({
    selector: 'jhi-exam-live-event',
    templateUrl: './exam-live-event.component.html',
    styleUrls: ['./exam-live-event.component.scss'],
})
export class ExamLiveEventComponent {
    @Input()
    event: ExamLiveEvent;

    @Input()
    showAcknowledge: boolean = false;

    @Output()
    onAcknowledge = new EventEmitter<ExamLiveEvent>();

    @Output()
    onNavigate = new EventEmitter<ExamLiveEvent>();

    protected readonly ExamLiveEventType = ExamLiveEventType;

    // Icons
    faCheck = faCheck;
    faPaperPlane = faPaperPlane;
    faEye = faEye;

    get examWideAnnouncementEvent(): ExamWideAnnouncementEvent {
        return this.event as ExamWideAnnouncementEvent;
    }

    get examAttendanceCheckEvent(): ExamAttendanceCheckEvent {
        return this.event as ExamAttendanceCheckEvent;
    }

    get workingTimeUpdateEvent(): WorkingTimeUpdateEvent {
        return this.event as WorkingTimeUpdateEvent;
    }

    get problemStatementUpdateEvent(): ProblemStatementUpdateEvent {
        return this.event as ProblemStatementUpdateEvent;
    }

    acknowledgeEvent() {
        this.onAcknowledge.emit(this.event);
    }

    navigateToExercise() {
        this.onNavigate.emit(this.event);
    }
}
