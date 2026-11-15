import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockModule, MockProvider } from 'ng-mocks';
import { ExamLiveEventsButtonComponent } from 'app/exam/overview/events/button/exam-live-events-button.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { ExamLiveEvent, ExamLiveEventType, ExamParticipationLiveEventsService } from 'app/exam/overview/services/exam-participation-live-events.service';
import { of } from 'rxjs';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MockExamParticipationLiveEventsService } from 'test/helpers/mocks/service/mock-exam-participation-live-events.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('ExamLiveEventsButtonComponent', () => {
    let component: ExamLiveEventsButtonComponent;
    let fixture: ComponentFixture<ExamLiveEventsButtonComponent>;
    let mockLiveEventsService: ExamParticipationLiveEventsService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamLiveEventsButtonComponent, MockModule(FontAwesomeModule)],
            providers: [
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ExamParticipationLiveEventsService, useClass: MockExamParticipationLiveEventsService },
            ],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(ExamLiveEventsButtonComponent);
        component = fixture.componentInstance;
        mockLiveEventsService = TestBed.inject(ExamParticipationLiveEventsService);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize eventCount based on all observed events', () => {
        // @ts-ignore
        const mockEvents: ExamLiveEvent[] = [{ eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT }, { eventType: ExamLiveEventType.WORKING_TIME_UPDATE }];
        vi.spyOn(mockLiveEventsService, 'observeAllEvents').mockReturnValue(of(mockEvents));
        component.ngOnInit();
        expect(component.eventCount()).toBe(2);
    });

    it('should open dialog when new events are observed', () => {
        expect(component.dialogVisible()).toBe(false);
        vi.spyOn(mockLiveEventsService, 'observeNewEventsAsUser').mockReturnValue(of({} as any as ExamLiveEvent));
        component.ngOnInit();
        expect(component.dialogVisible()).toBe(true);
    });

    it('should open the dialog when the button is clicked and keep it open for further events', () => {
        const preventDefault = vi.fn();
        component.openDialog({ preventDefault } as unknown as MouseEvent);
        expect(preventDefault).toHaveBeenCalledOnce();
        expect(component.dialogVisible()).toBe(true);
    });
});
