import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExamLiveEventsOverlayComponent } from 'app/exam/overview/events/exam-live-events-overlay.component';
import { ExamLiveEvent, ExamLiveEventType, ExamParticipationLiveEventsService } from 'app/exam/overview/exam-participation-live-events.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { ExamExerciseUpdateService } from 'app/exam/manage/exam-exercise-update.service';
import { MockLocalStorageService } from '../../../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../../helpers/mocks/service/mock-sync-storage.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockProvider } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { model } from '@angular/core';

describe('ExamLiveEventsOverlayComponent', () => {
    let component: ExamLiveEventsOverlayComponent;
    let fixture: ComponentFixture<ExamLiveEventsOverlayComponent>;
    let mockLiveEventsService: ExamParticipationLiveEventsService;
    let mockExamExerciseUpdateService: ExamExerciseUpdateService;
    let mockActiveModal: NgbActiveModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(NgbActiveModal),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamLiveEventsOverlayComponent);
        component = fixture.componentInstance;
        mockLiveEventsService = TestBed.inject(ExamParticipationLiveEventsService);
        mockExamExerciseUpdateService = TestBed.inject(ExamExerciseUpdateService);
        mockActiveModal = TestBed.inject(NgbActiveModal);
        TestBed.runInInjectionContext(() => {
            component.examStartDate = model(dayjs());
        });
        fixture.detectChanges();
    });

    it('should initialize unacknowledgedEvents and events based on observed events', () => {
        const mockEvents: ExamLiveEvent[] = [
            { id: 1, eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT } as any as ExamLiveEvent,
            { id: 2, eventType: ExamLiveEventType.WORKING_TIME_UPDATE } as any as ExamLiveEvent,
        ];
        jest.spyOn(mockLiveEventsService, 'observeAllEvents').mockReturnValue(of(mockEvents));
        jest.spyOn(mockLiveEventsService, 'observeNewEventsAsUser').mockReturnValue(of(mockEvents[0]));

        component.ngOnInit();

        expect(component.events).toEqual(mockEvents);
        expect(component.unacknowledgedEvents).toEqual([mockEvents[0]]);
    });

    it('should acknowledge an event', () => {
        const eventToAcknowledge: ExamLiveEvent = { id: 1, eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT } as any as ExamLiveEvent;
        component.unacknowledgedEvents = [eventToAcknowledge];

        jest.spyOn(mockLiveEventsService, 'acknowledgeEvent');

        component.acknowledgeEvent(eventToAcknowledge);

        expect(mockLiveEventsService.acknowledgeEvent).toHaveBeenCalledWith(eventToAcknowledge, true);
        expect(component.unacknowledgedEvents).toHaveLength(0);
    });

    it('should acknowledge all events', () => {
        const eventsToAcknowledge: ExamLiveEvent[] = [
            { id: 1, eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT } as any as ExamLiveEvent,
            { id: 2, eventType: ExamLiveEventType.WORKING_TIME_UPDATE } as any as ExamLiveEvent,
        ];
        component.unacknowledgedEvents = eventsToAcknowledge;

        jest.spyOn(mockLiveEventsService, 'acknowledgeEvent');

        component.acknowledgeAllUnacknowledgedEvents();

        expect(mockLiveEventsService.acknowledgeEvent).toHaveBeenCalledTimes(2);
        expect(mockLiveEventsService.acknowledgeEvent).toHaveBeenCalledWith(eventsToAcknowledge[0], true);
        expect(mockLiveEventsService.acknowledgeEvent).toHaveBeenCalledWith(eventsToAcknowledge[1], true);
        expect(component.unacknowledgedEvents).toHaveLength(0);
    });

    it('should close overlay', () => {
        jest.spyOn(mockActiveModal, 'close');

        component.closeOverlay();

        expect(mockActiveModal.close).toHaveBeenCalledWith('cancel');
    });

    it('should update events to display based on unacknowledgedEvents', () => {
        const mockEvents: ExamLiveEvent[] = [
            { id: 1, eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT } as any as ExamLiveEvent,
            { id: 2, eventType: ExamLiveEventType.WORKING_TIME_UPDATE } as any as ExamLiveEvent,
        ];
        component.events = mockEvents;
        component.unacknowledgedEvents = [mockEvents[0]];

        component.updateEventsToDisplay();

        expect(component.eventsToDisplay).toEqual([mockEvents[0]]);
    });

    it('should navigate to an exercise and acknowledge an event', () => {
        const event: ExamLiveEvent = { id: 1, eventType: ExamLiveEventType.PROBLEM_STATEMENT_UPDATE } as any as ExamLiveEvent;
        component.unacknowledgedEvents = [event];

        jest.spyOn(mockExamExerciseUpdateService, 'navigateToExamExercise');
        jest.spyOn(component, 'acknowledgeEvent');

        component.navigateToExercise(event);

        expect(mockExamExerciseUpdateService.navigateToExamExercise).toHaveBeenCalledOnce();
        expect(component.acknowledgeEvent).toHaveBeenCalledWith(event);
        expect(component.unacknowledgedEvents).toHaveLength(0);
    });
});
