import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { ExamLiveEventsOverlayComponent } from 'app/exam/participate/events/exam-live-events-overlay.component';
import { ExamLiveEvent, ExamLiveEventType, ExamParticipationLiveEventsService } from 'app/exam/participate/exam-participation-live-events.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { MockExamParticipationLiveEventsService } from '../../../../helpers/mocks/service/mock-exam-participation-live-events.service';

describe('ExamLiveEventsOverlayComponent', () => {
    let component: ExamLiveEventsOverlayComponent;
    let fixture: ComponentFixture<ExamLiveEventsOverlayComponent>;
    let mockLiveEventsService: ExamParticipationLiveEventsService;
    let mockActiveModal: NgbActiveModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [ExamLiveEventsOverlayComponent],
            providers: [{ provide: ExamParticipationLiveEventsService, useClass: MockExamParticipationLiveEventsService }, MockProvider(NgbActiveModal)],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(ExamLiveEventsOverlayComponent);
        component = fixture.componentInstance;
        mockLiveEventsService = TestBed.inject(ExamParticipationLiveEventsService);
        mockActiveModal = TestBed.inject(NgbActiveModal);
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
});
