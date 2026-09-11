import { AdminTitleBarComponent } from 'app/admin/shared/admin-title-bar/admin-title-bar.component';
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
    let titleBar: ComponentFixture<AdminTitleBarComponent>;
    let api: { getGenerationWorkers: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        api = { getGenerationWorkers: vi.fn(() => of(WORKERS)) };
        TestBed.configureTestingModule({
            imports: [HyperionWorkersComponent, AdminTitleBarComponent],
            providers: [
                { provide: AdminHyperionWorkerApi, useValue: api },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        fixture = TestBed.createComponent(HyperionWorkersComponent);
        titleBar = TestBed.createComponent(AdminTitleBarComponent);
    });

    function refresh(): void {
        titleBar.detectChanges();
        const button = titleBar.nativeElement.querySelector('[data-testid="hyperion-workers-refresh"] button') as HTMLButtonElement;
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

    it('distinguishes occupied capacity and retained offline reservations', () => {
        api.getGenerationWorkers.mockReturnValue(of([...WORKERS, { workerId: 'busy', state: 'BUSY', leaseHeld: true }]));
        fixture.detectChanges();
        expect(fixture.componentInstance['occupied']()).toBe(1);
        expect(fixture.componentInstance['unavailable']()).toBe(1);
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-workers-retained-reservations"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelectorAll('details')).toHaveLength(3);
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-workers-updated"]')).not.toBeNull();
    });

    it('refreshes visible pages periodically and stops polling after destruction', () => {
        vi.useFakeTimers();
        const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
        try {
            fixture.detectChanges();
            vi.advanceTimersByTime(15_000);
            expect(api.getGenerationWorkers).toHaveBeenCalledTimes(2);
            visibility.mockReturnValue('hidden');
            vi.advanceTimersByTime(15_000);
            expect(api.getGenerationWorkers).toHaveBeenCalledTimes(2);
            fixture.destroy();
            vi.advanceTimersByTime(15_000);
            expect(api.getGenerationWorkers).toHaveBeenCalledTimes(2);
        } finally {
            visibility.mockRestore();
            vi.useRealTimers();
        }
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
