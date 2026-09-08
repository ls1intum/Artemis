import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';
import { HyperionWorkersComponent } from './hyperion-workers.component';
import { AdminHyperionWorkerApi } from 'app/openapi/api/admin-hyperion-worker-api';
import { GenerationWorkerStatus } from 'app/openapi/model/generation-worker-status';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

const WORKERS: GenerationWorkerStatus[] = [
    { workerId: 'ready-worker', state: 'AVAILABLE', lastHeartbeat: '2026-09-08T12:00:00Z', imageDigest: 'sha256:abc', leaseHeld: false },
    { workerId: 'offline-worker', state: 'OFFLINE', leaseHeld: true },
];

describe('HyperionWorkersComponent', () => {
    let fixture: ComponentFixture<HyperionWorkersComponent>;
    let api: { getGenerationWorkers: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        api = { getGenerationWorkers: vi.fn(() => of(WORKERS)) };
        TestBed.configureTestingModule({
            imports: [HyperionWorkersComponent],
            providers: [
                { provide: AdminHyperionWorkerApi, useValue: api },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        fixture = TestBed.createComponent(HyperionWorkersComponent);
    });

    function refresh(): void {
        const button = fixture.nativeElement.querySelector('[data-testid="hyperion-workers-refresh"] button') as HTMLButtonElement;
        button.click();
        fixture.detectChanges();
    }

    it('loads once and renders configured offline workers alongside available capacity', () => {
        fixture.detectChanges();
        expect(api.getGenerationWorkers).toHaveBeenCalledOnce();
        expect(fixture.componentInstance['available']()).toBe(1);
        const rows = fixture.nativeElement.querySelectorAll('tbody tr');
        expect(rows).toHaveLength(2);
        expect(rows[0].textContent).toContain('ready-worker');
        expect(rows[0].textContent).toContain('sha256:abc');
        expect(rows[1].getAttribute('data-state')).toBe('OFFLINE');
        expect(rows[1].textContent).toContain('—');
        expect(fixture.nativeElement.querySelector('caption').textContent).toContain('artemisApp.hyperion.workers.title');
    });

    it('keeps the previous snapshot on failure and refreshes only when requested', () => {
        fixture.detectChanges();
        api.getGenerationWorkers.mockReturnValue(throwError(() => new Error('offline')));
        refresh();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-workers-error"]')).not.toBeNull();
        expect(fixture.componentInstance['workers']()).toEqual(WORKERS);
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-workers-summary"]')).toBeNull();
        api.getGenerationWorkers.mockReturnValue(of([]));
        refresh();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-workers-error"]')).toBeNull();
        expect(fixture.componentInstance['workers']()).toEqual([]);
    });

    it('prevents overlapping refreshes and unsubscribes when leaving the page', () => {
        const pending = new Subject<GenerationWorkerStatus[]>();
        api.getGenerationWorkers.mockReturnValue(pending);
        fixture.detectChanges();
        fixture.componentInstance['refresh']();
        expect(api.getGenerationWorkers).toHaveBeenCalledOnce();
        expect(fixture.nativeElement.querySelector('[aria-busy]').getAttribute('aria-busy')).toBe('true');
        fixture.destroy();
        expect(pending.observed).toBe(false);
    });
});
