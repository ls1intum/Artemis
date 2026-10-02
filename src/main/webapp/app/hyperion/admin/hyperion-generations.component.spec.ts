import { provideRouter } from '@angular/router';
import { AdminTitleBarComponent } from 'app/admin/shared/admin-title-bar/admin-title-bar.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
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
    let api: { getWorkers: ReturnType<typeof vi.fn> };

    const generationApi = { getActiveGenerations: vi.fn(() => of([])), cancelGeneration: vi.fn(() => of(undefined)) };

    beforeEach(() => {
        vi.clearAllMocks();
        generationApi.getActiveGenerations.mockReturnValue(of([]));
        generationApi.cancelGeneration.mockReturnValue(of(undefined));
        api = { getWorkers: vi.fn(() => of(WORKERS)) };
        TestBed.configureTestingModule({
            imports: [HyperionGenerationsComponent, AdminTitleBarComponent],
            providers: [
                provideRouter([]),
                { provide: AdminHyperionGenerationMonitoringApi, useValue: generationApi },
                { provide: AdminAiWorkerApi, useValue: api },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        fixture = TestBed.createComponent(HyperionGenerationsComponent);
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
