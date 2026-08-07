import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject, of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { BuildAgentsService } from 'app/localci/build-agents.service';
import { HyperionGenerationDetailComponent } from 'app/localci/hyperion-generation-detail/hyperion-generation-detail.component';

describe('HyperionGenerationDetailComponent', () => {
    let fixture: ComponentFixture<HyperionGenerationDetailComponent>;
    const service = {
        getGenerationSandboxes: vi.fn(),
        cancelGeneration: vi.fn(),
    };
    const jobs = [
        {
            sessionId: 'agent-1::0123456789abcdef0123456789abcdef',
            jobId: 'job-1',
            exerciseId: 42,
            exerciseTitle: 'Concurrency Lab',
            courseId: 7,
            userLogin: 'instructor',
            mode: 'GENERATE',
            startedAt: '2026-07-12T09:00:00Z',
            lastActivityAt: '2026-07-12T09:01:00Z',
        },
    ];

    beforeEach(() => {
        vi.clearAllMocks();
        service.getGenerationSandboxes.mockReturnValue(of(jobs));
        service.cancelGeneration.mockReturnValue(of(undefined));
        TestBed.configureTestingModule({
            imports: [HyperionGenerationDetailComponent],
            providers: [
                { provide: BuildAgentsService, useValue: service },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: convertToParamMap({ jobId: 'job-1' }),
                            queryParamMap: convertToParamMap({ agentName: 'agent-1' }),
                        },
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });
        fixture = TestBed.createComponent(HyperionGenerationDetailComponent);
    });

    it('loads the operational generation as its single sandbox job', () => {
        fixture.detectChanges();

        expect(service.getGenerationSandboxes).toHaveBeenCalledWith('agent-1');
        expect(fixture.componentInstance.job()).toEqual(expect.objectContaining({ jobId: 'job-1', exerciseId: 42, sessionId: jobs[0].sessionId }));
        expect(fixture.componentInstance.notFound()).toBe(false);
    });

    it('shows not found when the job is no longer active', () => {
        service.getGenerationSandboxes.mockReturnValue(of([]));

        fixture.detectChanges();

        expect(fixture.componentInstance.notFound()).toBe(true);
    });

    it('keeps load failures distinct from a completed job', () => {
        service.getGenerationSandboxes.mockReturnValue(throwError(() => new Error('offline')));

        fixture.detectChanges();

        expect(fixture.componentInstance.loadFailed()).toBe(true);
        expect(fixture.componentInstance.notFound()).toBe(false);
    });

    it('preserves the last-known job when a generation naturally ends', async () => {
        vi.useFakeTimers();
        // Re-create the component under fake timers so its elapsed-time clock is driven by them too. The original must be
        // destroyed first: change detection ticks every attached view, so leaving it alive would double every request.
        fixture.destroy();
        fixture = TestBed.createComponent(HyperionGenerationDetailComponent);
        service.getGenerationSandboxes.mockReturnValueOnce(of(jobs)).mockReturnValueOnce(of([]));
        fixture.detectChanges();
        expect(service.getGenerationSandboxes).toHaveBeenCalledTimes(1);

        await vi.advanceTimersByTimeAsync(5000);

        expect(service.getGenerationSandboxes).toHaveBeenCalledTimes(2);
        expect(fixture.componentInstance.naturallyEnded()).toBe(true);
        expect(fixture.componentInstance.job()?.jobId).toBe('job-1');
        const terminalDuration = fixture.componentInstance.elapsedSeconds();
        await vi.advanceTimersByTimeAsync(10_000);
        expect(fixture.componentInstance.elapsedSeconds()).toBe(terminalDuration);
        expect(service.getGenerationSandboxes).toHaveBeenCalledTimes(2);
        fixture.destroy();
        vi.useRealTimers();
    });

    it('announces release only after a requested cancellation disappears', () => {
        const cancellation = new Subject<void>();
        service.cancelGeneration.mockReturnValue(cancellation);
        fixture.detectChanges();

        fixture.componentInstance.confirmCancel();
        expect(fixture.componentInstance.confirmCancelVisible()).toBe(true);
        fixture.componentInstance.acceptCancel();
        expect(fixture.componentInstance.confirmCancelVisible()).toBe(false);

        expect(service.cancelGeneration).toHaveBeenCalledWith(42, 'job-1');
        expect(fixture.componentInstance.canceling()).toBe(true);

        service.getGenerationSandboxes.mockReturnValueOnce(of([]));
        cancellation.next();
        cancellation.complete();

        expect(service.getGenerationSandboxes).toHaveBeenCalledTimes(2);
        expect(fixture.componentInstance.released()).toBe(true);
        expect(fixture.componentInstance.job()?.jobId).toBe('job-1');
    });

    it('keeps last-known data when a background refresh fails', () => {
        service.getGenerationSandboxes.mockReturnValueOnce(of(jobs)).mockReturnValueOnce(throwError(() => new Error('offline')));
        fixture.detectChanges();

        fixture.componentInstance.load(false);

        expect(fixture.componentInstance.backgroundRefreshFailed()).toBe(true);
        expect(fixture.componentInstance.job()?.jobId).toBe('job-1');
    });

    it('does not overlap slow background refreshes', async () => {
        vi.useFakeTimers();
        const pending = new Subject<typeof jobs>();
        service.getGenerationSandboxes.mockReturnValue(pending);
        fixture.detectChanges();

        await vi.advanceTimersByTimeAsync(15_000);

        expect(service.getGenerationSandboxes).toHaveBeenCalledTimes(1);
        pending.next(jobs);
        pending.complete();
        await vi.advanceTimersByTimeAsync(5_000);
        expect(service.getGenerationSandboxes.mock.calls.length).toBeGreaterThan(1);
        fixture.destroy();
        vi.useRealTimers();
    });

    it('renders localized metadata and the single full container identifier directly', () => {
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('artemisApp.buildAgents.generationSandboxes.generate');
        expect(fixture.nativeElement.textContent).not.toContain('GENERATE');
        expect(fixture.nativeElement.textContent).toContain('Concurrency Lab');
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-container-id"]').textContent).toContain('0123456789abcdef0123456789abcdef');
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-sessions-scroll"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('p-table')).toBeNull();
    });

    it('presents the last sandbox activity as a self-updating relative time', () => {
        service.getGenerationSandboxes.mockReturnValue(of([{ ...jobs[0], lastActivityAt: dayjs().subtract(5, 'minutes').toISOString() }]));

        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('5 minutes ago');
    });

    it('moves focus to the stable back link when the job becomes terminal', () => {
        fixture.detectChanges();

        fixture.componentInstance.released.set(true);
        fixture.detectChanges();

        expect(document.activeElement).toBe(fixture.nativeElement.querySelector('[data-testid="back-to-build-agent"]'));
    });

    it('does not steal focus when a background refresh observes natural completion', () => {
        fixture.detectChanges();
        const containerId: HTMLElement = fixture.nativeElement.querySelector('[data-testid="hyperion-container-id"]');
        containerId.tabIndex = 0;
        containerId.focus();

        fixture.componentInstance.naturallyEnded.set(true);
        fixture.detectChanges();

        expect(document.activeElement).toBe(containerId);
    });
});
