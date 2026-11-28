import { ConnectionState, WebsocketService } from 'app/foundation/service/websocket.service';
import { ActiveGeneration } from 'app/openapi/model/active-generation';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { provideRouter } from '@angular/router';
import { AdminTitleBarComponent } from 'app/admin/shared/admin-title-bar/admin-title-bar.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Observable, Subject, of, throwError } from 'rxjs';
import { HyperionGenerationsComponent } from './hyperion-generations.component';
import { AdminAiWorkerApi } from 'app/openapi/api/admin-ai-worker-api';
import { WorkerStatus } from 'app/openapi/model/worker-status';
import { AdminHyperionGenerationMonitoringApi } from 'app/openapi/api/admin-hyperion-generation-monitoring-api';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

const WORKERS: WorkerStatus[] = [
    {
        workerId: 'ready-worker',
        state: 'AVAILABLE',
        lastHeartbeat: '2026-09-08T12:00:00Z',
        imageDigest: 'sha256:abc',
        leaseHeld: false,
        capacity: 1,
        availableSlots: 1,
        capability: { workload: 'hyperion-generation', version: 1, profile: 'java-gradle' },
    },
    { workerId: 'offline-worker', state: 'OFFLINE', leaseHeld: true },
];

describe('HyperionGenerationsComponent', () => {
    let fixture: ComponentFixture<HyperionGenerationsComponent>;
    let workerUpdates: Subject<WorkerStatus[]>;
    let generationUpdates: Subject<ActiveGeneration[]>;
    let connectionState: Subject<ConnectionState>;
    let api: { getWorkers: ReturnType<typeof vi.fn> };
    let subscribe: ReturnType<typeof vi.fn>;

    const generationApi = { getActiveGenerations: vi.fn(() => of<ActiveGeneration[]>([])), cancelGeneration: vi.fn(() => of(undefined)) };

    beforeEach(() => {
        vi.clearAllMocks();
        workerUpdates = new Subject<WorkerStatus[]>();
        generationUpdates = new Subject<ActiveGeneration[]>();
        connectionState = new Subject<ConnectionState>();
        generationApi.getActiveGenerations.mockReturnValue(of<ActiveGeneration[]>([]));
        generationApi.cancelGeneration.mockReturnValue(of(undefined));
        api = { getWorkers: vi.fn(() => of(WORKERS)) };
        subscribe = vi.fn((topic: string) => (topic.endsWith('ai-workers') ? workerUpdates : generationUpdates));
        TestBed.configureTestingModule({
            imports: [HyperionGenerationsComponent, AdminTitleBarComponent],
            providers: [
                { provide: WebsocketService, useValue: { subscribe, connectionState } },
                { provide: ProfileService, useValue: { isModuleFeatureActive: () => true } },
                provideRouter([]),
                { provide: AdminHyperionGenerationMonitoringApi, useValue: generationApi },
                { provide: AdminAiWorkerApi, useValue: api },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        fixture = TestBed.createComponent(HyperionGenerationsComponent);
    });

    it('loads once and receives live snapshots without polling, then unsubscribes on destruction', () => {
        vi.useFakeTimers();
        try {
            fixture.detectChanges();
            expect(subscribe).toHaveBeenCalledWith('/topic/admin/ai-workers');
            expect(subscribe).toHaveBeenCalledWith('/topic/admin/hyperion-generations');
            vi.advanceTimersByTime(60_000);
            expect(api.getWorkers).toHaveBeenCalledOnce();
            expect(generationApi.getActiveGenerations).toHaveBeenCalledOnce();
            workerUpdates.next([{ workerId: 'new-worker', state: 'AVAILABLE', capacity: 6, availableSlots: 5 }]);
            generationUpdates.next([{ jobId: 'live-run', exerciseId: 4 }]);
            expect(fixture.componentInstance['workers']()[0].workerId).toBe('new-worker');
            expect(fixture.componentInstance['generations']()[0].jobId).toBe('live-run');
            fixture.destroy();
            expect(workerUpdates.observed).toBe(false);
            expect(generationUpdates.observed).toBe(false);
        } finally {
            vi.useRealTimers();
        }
    });

    it('replaces an in-flight refresh and cancels subscriptions when leaving', () => {
        let subscriptions = 0;
        let unsubscriptions = 0;
        api.getWorkers.mockImplementation(
            () =>
                new Observable<WorkerStatus[]>(() => {
                    subscriptions++;
                    return () => unsubscriptions++;
                }),
        );
        fixture.detectChanges();
        fixture.componentInstance['refresh']();
        expect(api.getWorkers).toHaveBeenCalledTimes(2);
        expect(subscriptions).toBe(2);
        expect(unsubscriptions).toBe(1);
        fixture.destroy();
        expect(unsubscriptions).toBe(2);
    });

    it('does not let an older REST reply replace newer live snapshots', () => {
        const pendingWorkers = new Subject<WorkerStatus[]>();
        const pendingGenerations = new Subject<ActiveGeneration[]>();
        api.getWorkers.mockReturnValue(pendingWorkers);
        generationApi.getActiveGenerations.mockReturnValue(pendingGenerations);
        fixture.detectChanges();

        workerUpdates.next([{ workerId: 'live-worker', state: 'AVAILABLE', capacity: 2, availableSlots: 1 }]);
        generationUpdates.next([{ jobId: 'live-run', exerciseId: 4 }]);
        pendingWorkers.next(WORKERS);
        pendingWorkers.complete();
        pendingGenerations.next([{ jobId: 'old-run', exerciseId: 4 }]);
        pendingGenerations.complete();

        expect(fixture.componentInstance['workers']()[0].workerId).toBe('live-worker');
        expect(fixture.componentInstance['generations']()[0].jobId).toBe('live-run');
        expect(fixture.componentInstance['workersUpdatedAt']()).toBeDefined();
        expect(fixture.componentInstance['generationsUpdatedAt']()).toBeDefined();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-admin-run-page"]')).toBeNull();
    });

    it('tracks the two feed times independently and refreshes after reconnect', () => {
        fixture.detectChanges();
        const workerTime = fixture.componentInstance['workersUpdatedAt']();
        generationUpdates.next([{ jobId: 'live-run', exerciseId: 4 }]);
        expect(fixture.componentInstance['workersUpdatedAt']()).toBe(workerTime);
        expect(fixture.nativeElement.querySelector('[data-testid="ai-workers-updated"]')).not.toBeNull();

        connectionState.next(new ConnectionState(false, true));
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-live-updates-paused"]')).not.toBeNull();
        connectionState.next(new ConnectionState(true, true));
        fixture.detectChanges();
        expect(api.getWorkers).toHaveBeenCalledTimes(2);
        expect(generationApi.getActiveGenerations).toHaveBeenCalledTimes(2);
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-live-updates-paused"]')).toBeNull();
    });

    it('keeps worker capacity visible when generation monitoring fails', () => {
        generationApi.getActiveGenerations.mockReturnValue(throwError(() => new Error('generation unavailable')));
        fixture.detectChanges();
        expect(fixture.componentInstance['failed']()).toBe(true);
        expect(fixture.componentInstance['workersFailed']()).toBe(false);
        expect(fixture.nativeElement.querySelectorAll('[data-testid="ai-workers-table"] tbody tr')).toHaveLength(2);
    });

    it('retains the last worker snapshot and marks it stale when diagnostics fail', () => {
        fixture.detectChanges();
        api.getWorkers.mockReturnValue(throwError(() => new Error('offline')));
        fixture.componentInstance['refresh']();
        fixture.detectChanges();
        expect(fixture.componentInstance['workers']()).toEqual(WORKERS);
        expect(fixture.nativeElement.querySelector('[data-testid="ai-workers-error"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="ai-workers-summary"]')).toBeNull();
        expect(fixture.componentInstance['failed']()).toBe(false);
    });

    it('keeps workload cancellation available when worker diagnostics fail', () => {
        api.getWorkers.mockReturnValue(throwError(() => new Error('diagnostics offline')));
        fixture.detectChanges();
        expect(fixture.componentInstance['failed']()).toBe(false);
        expect(generationApi.getActiveGenerations).toHaveBeenCalledOnce();
    });

    it('requires a reason and retains it if an exact-run cancellation is rejected', () => {
        fixture.detectChanges();
        const component = fixture.componentInstance;
        component['selectCancellation']({ exerciseId: 42, jobId: 'selected-run', cancellable: true });
        component['cancelGeneration']();
        expect(generationApi.cancelGeneration).not.toHaveBeenCalled();
        component['cancelReason'].set('Stop this run only');
        generationApi.cancelGeneration.mockReturnValue(throwError(() => new Error('Already saving')));
        component['cancelGeneration']();
        expect(generationApi.cancelGeneration).toHaveBeenCalledWith(42, 'selected-run', 'Stop this run only');
        expect(component['cancelDialogVisible']()).toBe(true);
        expect(component['cancelReason']()).toBe('Stop this run only');
        expect(component['cancelFailed']()).toBe(true);
        expect(component['canSubmitCancellation']()).toBe(false);
    });

    it('never offers cancellation for a saving or already cancelled run', () => {
        fixture.detectChanges();
        const component = fixture.componentInstance;
        component['selectCancellation']({ exerciseId: 42, jobId: 'saving', cancellable: false });
        component['cancelReason'].set('Stop');
        component['cancelGeneration']();
        expect(generationApi.cancelGeneration).not.toHaveBeenCalled();
    });
});
