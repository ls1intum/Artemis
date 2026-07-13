import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject, of, throwError } from 'rxjs';
import { ConfirmationService } from 'primeng/api';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { BuildAgentsService } from 'app/localci/build-agents.service';
import { HyperionGenerationDetailComponent } from 'app/localci/hyperion-generation-detail/hyperion-generation-detail.component';

describe('HyperionGenerationDetailComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<HyperionGenerationDetailComponent>;
    const service = {
        getGenerationSandboxes: vi.fn(),
        cancelGeneration: vi.fn(),
    };
    const sessions = [
        {
            sessionId: 'authoring-session',
            role: 'AUTHORING',
            jobId: 'job-1',
            exerciseId: 42,
            exerciseTitle: 'Concurrency Lab',
            courseId: 7,
            userLogin: 'instructor',
            mode: 'GENERATE',
            startedAt: '2026-07-12T09:00:00Z',
            lastActivityAt: '2026-07-12T09:01:00Z',
            reservedSlots: 2,
        },
    ];

    beforeEach(() => {
        vi.clearAllMocks();
        service.getGenerationSandboxes.mockReturnValue(of(sessions));
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
            ],
        });
        fixture = TestBed.createComponent(HyperionGenerationDetailComponent);
    });

    it('loads the operational generation and its sessions', () => {
        fixture.componentInstance.ngOnInit();

        expect(service.getGenerationSandboxes).toHaveBeenCalledWith('agent-1');
        expect(fixture.componentInstance.job()).toEqual(expect.objectContaining({ jobId: 'job-1', exerciseId: 42, reservedSlots: 2 }));
        expect(fixture.componentInstance.notFound()).toBe(false);
    });

    it('shows not found when the job is no longer active', () => {
        service.getGenerationSandboxes.mockReturnValue(of([]));

        fixture.componentInstance.ngOnInit();

        expect(fixture.componentInstance.notFound()).toBe(true);
    });

    it('keeps load failures distinct from a completed job', () => {
        service.getGenerationSandboxes.mockReturnValue(throwError(() => new Error('offline')));

        fixture.componentInstance.ngOnInit();

        expect(fixture.componentInstance.loadFailed()).toBe(true);
        expect(fixture.componentInstance.notFound()).toBe(false);
    });

    it('preserves the last-known job when a generation naturally ends', async () => {
        vi.useFakeTimers();
        service.getGenerationSandboxes.mockReturnValueOnce(of(sessions)).mockReturnValueOnce(of([]));
        fixture.detectChanges();
        expect(service.getGenerationSandboxes).toHaveBeenCalledTimes(1);

        await vi.advanceTimersByTimeAsync(5000);

        expect(service.getGenerationSandboxes).toHaveBeenCalledTimes(2);
        expect(fixture.componentInstance.naturallyEnded()).toBe(true);
        expect(fixture.componentInstance.job()?.jobId).toBe('job-1');
        const terminalDuration = fixture.componentInstance.elapsedSeconds(sessions[0].startedAt);
        await vi.advanceTimersByTimeAsync(10_000);
        expect(fixture.componentInstance.elapsedSeconds(sessions[0].startedAt)).toBe(terminalDuration);
        fixture.componentInstance.ngOnDestroy();
        vi.useRealTimers();
    });

    it('announces release only after a requested cancellation disappears', () => {
        const cancellation = new Subject<void>();
        service.cancelGeneration.mockReturnValue(cancellation);
        fixture.componentInstance.ngOnInit();
        const confirmationService = fixture.debugElement.injector.get(ConfirmationService);
        const confirm = vi.spyOn(confirmationService, 'confirm');

        fixture.componentInstance.confirmCancel();
        confirm.mock.calls[0][0].accept?.();

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
        service.getGenerationSandboxes.mockReturnValueOnce(of(sessions)).mockReturnValueOnce(throwError(() => new Error('offline')));
        fixture.componentInstance.ngOnInit();

        fixture.componentInstance.load(false);

        expect(fixture.componentInstance.backgroundRefreshFailed()).toBe(true);
        expect(fixture.componentInstance.job()?.jobId).toBe('job-1');
    });

    it('does not overlap slow background refreshes', async () => {
        vi.useFakeTimers();
        const pending = new Subject<typeof sessions>();
        service.getGenerationSandboxes.mockReturnValue(pending);
        fixture.componentInstance.ngOnInit();

        await vi.advanceTimersByTimeAsync(15_000);

        expect(service.getGenerationSandboxes).toHaveBeenCalledTimes(1);
        pending.next(sessions);
        pending.complete();
        await vi.advanceTimersByTimeAsync(5_000);
        expect(service.getGenerationSandboxes.mock.calls.length).toBeGreaterThan(1);
        fixture.componentInstance.ngOnDestroy();
        vi.useRealTimers();
    });

    it('renders localized mode, responsive sessions, and a useful container identifier', () => {
        service.getGenerationSandboxes.mockReturnValue(of([{ ...sessions[0], sessionId: 'agent-1::0123456789abcdef0123456789abcdef' }]));

        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('artemisApp.buildAgents.generationSandboxes.generate');
        expect(fixture.nativeElement.textContent).not.toContain('GENERATE');
        expect(fixture.nativeElement.textContent).toContain('Concurrency Lab');
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-sessions-scroll"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('summary code').textContent).toContain('0123456789ab…');
        expect(fixture.nativeElement.querySelector('code[aria-label="0123456789abcdef0123456789abcdef"]').textContent).toContain('0123456789abcdef0123456789abcdef');
    });

    it('moves focus to the stable back link when the job becomes terminal', () => {
        fixture.detectChanges();

        fixture.componentInstance.released.set(true);
        fixture.detectChanges();

        expect(document.activeElement).toBe(fixture.nativeElement.querySelector('[data-testid="back-to-build-agent"]'));
    });

    it('does not steal focus when a background refresh observes natural completion', () => {
        fixture.detectChanges();
        const sessionToggle: HTMLElement = fixture.nativeElement.querySelector('summary');
        sessionToggle.focus();

        fixture.componentInstance.naturallyEnded.set(true);
        fixture.detectChanges();

        expect(document.activeElement).toBe(sessionToggle);
    });
});
