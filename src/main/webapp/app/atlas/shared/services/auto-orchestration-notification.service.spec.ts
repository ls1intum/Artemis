import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { AlertService } from 'app/shared/service/alert.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { AutoOrchestrationNotificationService, AutoOrchestrationSummary } from 'app/atlas/shared/services/auto-orchestration-notification.service';

describe('AutoOrchestrationNotificationService', () => {
    setupTestBed({ zoneless: true });

    let service: AutoOrchestrationNotificationService;
    let websocketSubject: Subject<AutoOrchestrationSummary>;
    let websocketSubscribeSpy: ReturnType<typeof vi.fn>;
    let alertSuccessSpy: ReturnType<typeof vi.fn>;
    let alertWarningSpy: ReturnType<typeof vi.fn>;
    let alertErrorSpy: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        websocketSubject = new Subject<AutoOrchestrationSummary>();
        websocketSubscribeSpy = vi.fn().mockReturnValue(websocketSubject);
        alertSuccessSpy = vi.fn();
        alertWarningSpy = vi.fn();
        alertErrorSpy = vi.fn();

        TestBed.configureTestingModule({
            providers: [
                AutoOrchestrationNotificationService,
                { provide: WebsocketService, useValue: { subscribe: websocketSubscribeSpy } },
                { provide: AlertService, useValue: { success: alertSuccessSpy, warning: alertWarningSpy, error: alertErrorSpy } },
            ],
        });

        service = TestBed.inject(AutoOrchestrationNotificationService);
    });

    it('subscribes to the course-scoped topic on first call only', () => {
        service.subscribeToCourse(42);
        service.subscribeToCourse(42);

        expect(websocketSubscribeSpy).toHaveBeenCalledTimes(1);
        expect(websocketSubscribeSpy).toHaveBeenCalledWith('/topic/atlas/orchestrator/42');
    });

    it('emits a success alert when all exercises succeed', () => {
        service.subscribeToCourse(42);

        websocketSubject.next(summary({ exerciseCount: 3, successCount: 3, failureCount: 0 }));

        expect(alertSuccessSpy).toHaveBeenCalledWith('artemisApp.atlasOrchestrator.autoToast.success', { count: 3, success: 3, failure: 0 });
        expect(alertWarningSpy).not.toHaveBeenCalled();
        expect(alertErrorSpy).not.toHaveBeenCalled();
    });

    it('emits a warning alert on partial failure', () => {
        service.subscribeToCourse(42);

        websocketSubject.next(summary({ exerciseCount: 3, successCount: 2, failureCount: 1 }));

        expect(alertWarningSpy).toHaveBeenCalledWith('artemisApp.atlasOrchestrator.autoToast.partial', { count: 3, success: 2, failure: 1 });
    });

    it('emits an error alert when every exercise failed', () => {
        service.subscribeToCourse(42);

        websocketSubject.next(summary({ exerciseCount: 2, successCount: 0, failureCount: 2 }));

        expect(alertErrorSpy).toHaveBeenCalledWith('artemisApp.atlasOrchestrator.autoToast.failure', { count: 2, success: 0, failure: 2 });
    });

    it('drops the subscription on unsubscribeFromCourse', () => {
        service.subscribeToCourse(42);
        service.unsubscribeFromCourse(42);

        websocketSubject.next(summary({ exerciseCount: 1, successCount: 1, failureCount: 0 }));

        expect(alertSuccessSpy).not.toHaveBeenCalled();

        // Re-subscribing creates a fresh stomp subscription.
        service.subscribeToCourse(42);
        expect(websocketSubscribeSpy).toHaveBeenCalledTimes(2);
    });

    function summary(overrides: Partial<AutoOrchestrationSummary>): AutoOrchestrationSummary {
        return {
            courseId: 42,
            runId: 'run-1',
            exerciseCount: 0,
            successCount: 0,
            failureCount: 0,
            completedAt: '2026-04-24T12:00:00Z',
            ...overrides,
        };
    }
});
