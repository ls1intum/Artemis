import { AdminTitleBarComponent } from 'app/admin/shared/admin-title-bar/admin-title-bar.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';
import { AiWorkersComponent } from './ai-workers.component';
import { AdminAiWorkerApi } from 'app/openapi/api/admin-ai-worker-api';
import { WorkerStatus } from 'app/openapi/model/worker-status';
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

describe('AiWorkersComponent', () => {
    let fixture: ComponentFixture<AiWorkersComponent>;
    let titleBar: ComponentFixture<AdminTitleBarComponent>;
    let api: { getWorkers: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        vi.clearAllMocks();
        api = { getWorkers: vi.fn(() => of(WORKERS)) };
        TestBed.configureTestingModule({
            imports: [AiWorkersComponent, AdminTitleBarComponent],
            providers: [
                { provide: AdminAiWorkerApi, useValue: api },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        fixture = TestBed.createComponent(AiWorkersComponent);
        titleBar = TestBed.createComponent(AdminTitleBarComponent);
    });

    function refresh(): void {
        titleBar.detectChanges();
        const button = titleBar.nativeElement.querySelector('[data-testid="ai-workers-refresh"] button') as HTMLButtonElement;
        button.click();
        fixture.detectChanges();
    }

    it('loads once and renders configured offline workers alongside available capacity', () => {
        fixture.detectChanges();
        expect(api.getWorkers).toHaveBeenCalledOnce();
        expect(fixture.componentInstance['available']()).toBe(1);
        const rows = fixture.nativeElement.querySelectorAll('tbody tr');
        expect(rows).toHaveLength(2);
        expect(rows[0].textContent).toContain('ready-worker');
        expect(rows[0].textContent).toContain('sha256:abc');
        expect(rows[1].getAttribute('data-state')).toBe('OFFLINE');
        expect(rows[1].textContent).toContain('—');
        expect(fixture.nativeElement.querySelector('caption').textContent).toContain('artemisApp.aiworker.title');
    });

    it('keeps the previous snapshot on failure and refreshes only when requested', () => {
        fixture.detectChanges();
        api.getWorkers.mockReturnValue(throwError(() => new Error('offline')));
        refresh();
        expect(fixture.nativeElement.querySelector('[data-testid="ai-workers-error"]')).not.toBeNull();
        expect(fixture.componentInstance['workers']()).toEqual(WORKERS);
        expect(fixture.nativeElement.querySelector('[data-testid="ai-workers-summary"]')).toBeNull();
        api.getWorkers.mockReturnValue(of([]));
        refresh();
        expect(fixture.nativeElement.querySelector('[data-testid="ai-workers-error"]')).toBeNull();
        expect(fixture.componentInstance['workers']()).toEqual([]);
    });

    it('distinguishes occupied capacity and retained offline reservations', () => {
        api.getWorkers.mockReturnValue(of([...WORKERS, { workerId: 'busy', state: 'BUSY', leaseHeld: true, capacity: 1, availableSlots: 0 }]));
        fixture.detectChanges();
        expect(fixture.componentInstance['occupied']()).toBe(1);
        expect(fixture.componentInstance['unavailable']()).toBe(1);
        expect(fixture.nativeElement.querySelector('[data-testid="ai-workers-retained-reservations"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelectorAll('details')).toHaveLength(3);
        expect(fixture.nativeElement.querySelector('[data-testid="ai-workers-updated"]')).not.toBeNull();
    });

    it('refreshes visible pages periodically and stops polling after destruction', () => {
        vi.useFakeTimers();
        const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
        try {
            fixture.detectChanges();
            vi.advanceTimersByTime(15_000);
            expect(api.getWorkers).toHaveBeenCalledTimes(2);
            visibility.mockReturnValue('hidden');
            vi.advanceTimersByTime(15_000);
            expect(api.getWorkers).toHaveBeenCalledTimes(2);
            fixture.destroy();
            vi.advanceTimersByTime(15_000);
            expect(api.getWorkers).toHaveBeenCalledTimes(2);
        } finally {
            visibility.mockRestore();
            vi.useRealTimers();
        }
    });

    it('prevents overlapping refreshes and unsubscribes when leaving the page', () => {
        const pending = new Subject<WorkerStatus[]>();
        api.getWorkers.mockReturnValue(pending);
        fixture.detectChanges();
        fixture.componentInstance['refresh']();
        expect(api.getWorkers).toHaveBeenCalledOnce();
        expect(fixture.nativeElement.querySelector('[aria-busy]').getAttribute('aria-busy')).toBe('true');
        fixture.destroy();
        expect(pending.observed).toBe(false);
    });
    it('counts free slots independently from the number of workers', () => {
        api.getWorkers.mockReturnValue(of([{ workerId: 'worker', state: 'AVAILABLE', capacity: 4, availableSlots: 3 }]));
        fixture.detectChanges();
        expect(fixture.componentInstance['available']()).toBe(3);
        expect(fixture.componentInstance['slots']()).toBe(4);
        expect(fixture.componentInstance['occupied']()).toBe(1);
        expect(fixture.componentInstance['workers']()).toHaveLength(1);
    });

    it('does not invent capacity or a toolchain for an offline worker', () => {
        api.getWorkers.mockReturnValue(of([{ workerId: 'offline', state: 'OFFLINE', capacity: 4, availableSlots: 4 }]));
        fixture.detectChanges();
        expect(fixture.componentInstance['slots']()).toBe(0);
        expect(fixture.componentInstance['available']()).toBe(0);
        expect(fixture.nativeElement.querySelector('[data-testid="ai-worker-capacity"]').textContent.trim()).toBe('—');
        expect(fixture.nativeElement.querySelector('[data-testid="ai-worker-toolchain"]').textContent.trim()).toBe('—');
    });

    it('shows the advertised toolchain and every occupied slot using one-based labels', () => {
        api.getWorkers.mockReturnValue(
            of([
                {
                    workerId: 'worker',
                    state: 'BUSY',
                    capacity: 2,
                    availableSlots: 0,
                    capability: { workload: 'hyperion-generation', version: 1, profile: 'java-gradle' },
                    executions: [
                        { slot: 0, executionId: 'execution-one' },
                        { slot: 1, executionId: 'execution-two' },
                    ],
                },
            ]),
        );
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="ai-worker-toolchain"]').textContent).toContain('hyperion-generation / java-gradle (v1)');
        const executions = fixture.nativeElement.querySelectorAll('[data-testid="ai-worker-execution"]');
        expect(executions).toHaveLength(2);
        expect(executions[0].textContent).toContain('1: execution-one');
        expect(executions[1].textContent).toContain('2: execution-two');
    });
});
