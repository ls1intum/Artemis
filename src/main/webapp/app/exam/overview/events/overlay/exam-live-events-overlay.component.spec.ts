import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExamLiveEventsOverlayComponent } from 'app/exam/overview/events/overlay/exam-live-events-overlay.component';
import { ExamLiveEvent, ExamLiveEventType, ExamParticipationLiveEventsService } from 'app/exam/overview/services/exam-participation-live-events.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { Subject, of } from 'rxjs';
import { ExamExerciseUpdateService } from 'app/exam/manage/services/exam-exercise-update.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { type Mock, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('ExamLiveEventsOverlayComponent', () => {
    let component: ExamLiveEventsOverlayComponent;
    let fixture: ComponentFixture<ExamLiveEventsOverlayComponent>;
    let mockLiveEventsService: ExamParticipationLiveEventsService;
    let mockExamExerciseUpdateService: ExamExerciseUpdateService;
    let closedSpy: Mock<() => void>;

    beforeEach(async () => {
        closedSpy = vi.fn<() => void>();

        await TestBed.configureTestingModule({
            providers: [SessionStorageService, provideHttpClient(), provideHttpClientTesting()],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(ExamLiveEventsOverlayComponent);
        component = fixture.componentInstance;
        component.closed.subscribe(closedSpy);
        mockLiveEventsService = TestBed.inject(ExamParticipationLiveEventsService);
        mockExamExerciseUpdateService = TestBed.inject(ExamExerciseUpdateService);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize unacknowledgedEvents and events based on observed events', () => {
        const mockEvents: ExamLiveEvent[] = [
            { id: 1, eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT } as any as ExamLiveEvent,
            { id: 2, eventType: ExamLiveEventType.WORKING_TIME_UPDATE } as any as ExamLiveEvent,
        ];
        vi.spyOn(mockLiveEventsService, 'observeAllEvents').mockReturnValue(of(mockEvents));
        vi.spyOn(mockLiveEventsService, 'observeNewEventsAsUser').mockReturnValue(of(mockEvents[0]));

        component.ngOnInit();

        expect(component.events()).toEqual(mockEvents);
        expect(component.unacknowledgedEvents()).toEqual([mockEvents[0]]);
    });

    it('should hide problem statement updates that were made before the start of the exam', () => {
        const examStart = dayjs('2026-01-01T10:00:00Z');
        fixture.componentRef.setInput('examStartDate', examStart);
        const before = { id: 1, eventType: ExamLiveEventType.PROBLEM_STATEMENT_UPDATE, createdDate: examStart.subtract(1, 'hour') } as any as ExamLiveEvent;
        const after = { id: 2, eventType: ExamLiveEventType.PROBLEM_STATEMENT_UPDATE, createdDate: examStart.add(1, 'hour') } as any as ExamLiveEvent;
        const announcement = { id: 3, eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT, createdDate: examStart.subtract(1, 'hour') } as any as ExamLiveEvent;
        vi.spyOn(mockLiveEventsService, 'observeAllEvents').mockReturnValue(of([before, after, announcement]));
        vi.spyOn(mockLiveEventsService, 'observeNewEventsAsUser').mockReturnValue(new Subject<ExamLiveEvent>());

        component.ngOnInit();

        expect(component.events()).toEqual([after, announcement]);
    });

    it('should acknowledge an event', () => {
        const eventToAcknowledge: ExamLiveEvent = { id: 1, eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT } as any as ExamLiveEvent;
        component.unacknowledgedEvents.set([eventToAcknowledge]);

        vi.spyOn(mockLiveEventsService, 'acknowledgeEvent');

        component.acknowledgeEvent(eventToAcknowledge);

        expect(mockLiveEventsService.acknowledgeEvent).toHaveBeenCalledWith(eventToAcknowledge, true);
        expect(component.unacknowledgedEvents()).toHaveLength(0);
    });

    it('should acknowledge all events', () => {
        const eventsToAcknowledge: ExamLiveEvent[] = [
            { id: 1, eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT } as any as ExamLiveEvent,
            { id: 2, eventType: ExamLiveEventType.WORKING_TIME_UPDATE } as any as ExamLiveEvent,
        ];
        component.unacknowledgedEvents.set(eventsToAcknowledge);

        vi.spyOn(mockLiveEventsService, 'acknowledgeEvent');

        component.acknowledgeAllUnacknowledgedEvents();

        expect(mockLiveEventsService.acknowledgeEvent).toHaveBeenCalledTimes(2);
        expect(mockLiveEventsService.acknowledgeEvent).toHaveBeenCalledWith(eventsToAcknowledge[0], true);
        expect(mockLiveEventsService.acknowledgeEvent).toHaveBeenCalledWith(eventsToAcknowledge[1], true);
        expect(component.unacknowledgedEvents()).toHaveLength(0);
    });

    it('should close overlay', () => {
        component.closeOverlay();

        expect(closedSpy).toHaveBeenCalledOnce();
    });

    it('should update events to display based on unacknowledgedEvents', () => {
        const mockEvents: ExamLiveEvent[] = [
            { id: 1, eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT } as any as ExamLiveEvent,
            { id: 2, eventType: ExamLiveEventType.WORKING_TIME_UPDATE } as any as ExamLiveEvent,
        ];
        component.events.set(mockEvents);
        component.unacknowledgedEvents.set([mockEvents[0]]);

        component.updateEventsToDisplay();

        expect(component.eventsToDisplay()).toEqual([mockEvents[0]]);
    });

    it('should navigate to an exercise and acknowledge an event', () => {
        const event: ExamLiveEvent = { id: 1, eventType: ExamLiveEventType.PROBLEM_STATEMENT_UPDATE } as any as ExamLiveEvent;
        component.unacknowledgedEvents.set([event]);

        vi.spyOn(mockExamExerciseUpdateService, 'navigateToExamExercise');
        vi.spyOn(component, 'acknowledgeEvent');

        component.navigateToExercise(event);

        expect(mockExamExerciseUpdateService.navigateToExamExercise).toHaveBeenCalledOnce();
        expect(component.acknowledgeEvent).toHaveBeenCalledWith(event);
        expect(component.unacknowledgedEvents()).toHaveLength(0);
    });
});
