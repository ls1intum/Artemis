import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockModule, MockProvider } from 'ng-mocks';
import { ExamLiveEventsButtonComponent } from 'app/exam/participate/events/exam-live-events-button.component';
import { AlertService } from 'app/core/util/alert.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ExamLiveEvent, ExamLiveEventType, ExamParticipationLiveEventsService } from 'app/exam/participate/exam-participation-live-events.service';
import { of } from 'rxjs';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MockExamParticipationLiveEventsService } from '../../../../helpers/mocks/service/mock-exam-participation-live-events.service';
import { ExamLiveEventsOverlayComponent } from 'app/exam/participate/events/exam-live-events-overlay.component';

describe('ExamLiveEventsButtonComponent', () => {
    let component: ExamLiveEventsButtonComponent;
    let fixture: ComponentFixture<ExamLiveEventsButtonComponent>;
    let mockModalService: NgbModal;
    let mockLiveEventsService: ExamParticipationLiveEventsService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [ExamLiveEventsButtonComponent],
            imports: [MockModule(FontAwesomeModule)],
            providers: [MockProvider(AlertService), MockProvider(NgbModal), { provide: ExamParticipationLiveEventsService, useClass: MockExamParticipationLiveEventsService }],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(ExamLiveEventsButtonComponent);
        component = fixture.componentInstance;
        mockModalService = TestBed.inject(NgbModal);
        mockLiveEventsService = TestBed.inject(ExamParticipationLiveEventsService);
        fixture.detectChanges();
    });

    it('should initialize eventCount based on all observed events', () => {
        // @ts-ignore
        const mockEvents: ExamLiveEvent[] = [{ eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT }, { eventType: ExamLiveEventType.WORKING_TIME_UPDATE }];
        jest.spyOn(mockLiveEventsService, 'observeAllEvents').mockReturnValue(of(mockEvents));
        component.ngOnInit();
        expect(component.eventCount).toBe(2);
    });

    it('should open dialog when new events are observed', () => {
        const mockModalSpy = jest.spyOn(mockModalService, 'open').mockReturnValue({ componentInstance: {} } as any);
        jest.spyOn(mockLiveEventsService, 'observeNewEventsAsUser').mockReturnValue(of({} as any as ExamLiveEvent));
        component.ngOnInit();
        expect(mockModalSpy).toHaveBeenCalledExactlyOnceWith(ExamLiveEventsOverlayComponent, {
            size: 'lg',
            backdrop: 'static',
            animation: false,
            centered: true,
            windowClass: 'live-events-modal-window',
        });
    });
});
