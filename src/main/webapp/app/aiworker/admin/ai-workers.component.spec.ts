import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { AiWorkersComponent } from './ai-workers.component';
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

    beforeEach(() => {
        vi.clearAllMocks();
        TestBed.configureTestingModule({
            imports: [AiWorkersComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        fixture = TestBed.createComponent(AiWorkersComponent);
        fixture.componentRef.setInput('workers', WORKERS);
        fixture.componentRef.setInput('updatedAt', new Date());
    });

    it('renders configured offline workers alongside available capacity', () => {
        fixture.detectChanges();
        expect(fixture.componentInstance['available']()).toBe(1);
        const rows = fixture.nativeElement.querySelectorAll('tbody tr');
        expect(rows).toHaveLength(2);
        expect(rows[0].textContent).toContain('ready-worker');
        expect(rows[0].textContent).toContain('sha256:abc');
        expect(rows[1].getAttribute('data-state')).toBe('OFFLINE');
        expect(rows[1].textContent).toContain('—');
        expect(fixture.nativeElement.querySelector('caption').textContent).toContain('artemisApp.aiworker.title');
    });

    it('marks stale data and hides capacity totals when the parent reports a failure', () => {
        fixture.componentRef.setInput('failed', true);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="ai-workers-error"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="ai-workers-summary"]')).toBeNull();
        expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(2);
    });

    it('distinguishes occupied capacity and retained offline reservations', () => {
        fixture.componentRef.setInput('workers', [...WORKERS, { workerId: 'busy', state: 'BUSY', leaseHeld: true, capacity: 1, availableSlots: 0 }]);
        fixture.detectChanges();
        expect(fixture.componentInstance['occupied']()).toBe(1);
        expect(fixture.componentInstance['unavailable']()).toBe(1);
        expect(fixture.nativeElement.querySelector('[data-testid="ai-workers-retained-reservations"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelectorAll('details')).toHaveLength(3);
    });

    it('counts free slots independently from the number of workers', () => {
        fixture.componentRef.setInput('workers', [{ workerId: 'worker', state: 'AVAILABLE', capacity: 4, availableSlots: 3 }]);
        fixture.detectChanges();
        expect(fixture.componentInstance['available']()).toBe(3);
        expect(fixture.componentInstance['slots']()).toBe(4);
        expect(fixture.componentInstance['occupied']()).toBe(1);
        expect(fixture.componentInstance['workers']()).toHaveLength(1);
    });

    it('does not invent capacity or a toolchain for an offline worker', () => {
        fixture.componentRef.setInput('workers', [{ workerId: 'offline', state: 'OFFLINE', capacity: 4, availableSlots: 4 }]);
        fixture.detectChanges();
        expect(fixture.componentInstance['slots']()).toBe(0);
        expect(fixture.componentInstance['available']()).toBe(0);
        expect(fixture.nativeElement.querySelector('[data-testid="ai-worker-capacity"]').textContent.trim()).toBe('—');
        expect(fixture.nativeElement.querySelector('[data-testid="ai-worker-toolchain"]').textContent.trim()).toBe('—');
    });

    it('shows the advertised toolchain and every occupied slot using one-based labels', () => {
        fixture.componentRef.setInput('workers', [
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
        ]);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="ai-worker-toolchain"]').textContent).toContain('hyperion-generation / java-gradle (v1)');
        const executions = fixture.nativeElement.querySelectorAll('[data-testid="ai-worker-execution"]');
        expect(executions).toHaveLength(2);
        expect(executions[0].textContent).toContain('1: execution-one');
        expect(executions[1].textContent).toContain('2: execution-two');
    });
});
