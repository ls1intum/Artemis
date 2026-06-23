import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockModule, MockProvider } from 'ng-mocks';
import { ExamLiveEventsButtonComponent } from 'app/exam/overview/events/button/exam-live-events-button.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { DialogService } from 'primeng/dynamicdialog';
import { ExamLiveEvent, ExamLiveEventType, ExamParticipationLiveEventsService } from 'app/exam/overview/services/exam-participation-live-events.service';
import { Subject, of } from 'rxjs';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MockExamParticipationLiveEventsService } from 'test/helpers/mocks/service/mock-exam-participation-live-events.service';
import { ExamLiveEventsOverlayComponent } from 'app/exam/overview/events/overlay/exam-live-events-overlay.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('ExamLiveEventsButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ExamLiveEventsButtonComponent;
    let fixture: ComponentFixture<ExamLiveEventsButtonComponent>;
    let mockDialogService: DialogService;
    let mockLiveEventsService: ExamParticipationLiveEventsService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamLiveEventsButtonComponent, MockModule(FontAwesomeModule)],
            providers: [
                MockProvider(AlertService),
                MockProvider(DialogService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ExamParticipationLiveEventsService, useClass: MockExamParticipationLiveEventsService },
            ],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(ExamLiveEventsButtonComponent);
        component = fixture.componentInstance;
        mockDialogService = TestBed.inject(DialogService);
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
        const dialogSpy = vi.spyOn(mockDialogService, 'open').mockReturnValue({ onClose: new Subject<any>() } as any);
        vi.spyOn(mockLiveEventsService, 'observeNewEventsAsUser').mockReturnValue(of({} as any as ExamLiveEvent));
        component.ngOnInit();
        expect(dialogSpy).toHaveBeenCalledOnce();
        expect(dialogSpy.mock.calls[0][0]).toBe(ExamLiveEventsOverlayComponent);
        const config = dialogSpy.mock.calls[0][1];
        expect(config?.modal).toBe(true);
        expect(config?.styleClass).toBe('live-events-modal-window');
        expect((config?.data as { examStartDate?: unknown } | undefined)?.examStartDate).toBeUndefined();
    });
});
