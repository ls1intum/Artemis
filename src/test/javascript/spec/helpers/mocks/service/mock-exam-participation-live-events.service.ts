import { ExamLiveEvent, ExamLiveEventType } from 'app/exam/participate/exam-participation-live-events.service';
import { Observable, of } from 'rxjs';

export class MockExamParticipationLiveEventsService {
    public observeNewEventsAsSystem(eventTypes: ExamLiveEventType[] = []): Observable<ExamLiveEvent> {
        return of();
    }

    public observeNewEventsAsUser(eventTypes: ExamLiveEventType[] = []): Observable<ExamLiveEvent> {
        return of();
    }

    public observeAllEvents(eventTypes: ExamLiveEventType[] = []): Observable<ExamLiveEvent[]> {
        return of();
    }

    public acknowledgeEvent(event: ExamLiveEvent, byUser: boolean) {}
}
