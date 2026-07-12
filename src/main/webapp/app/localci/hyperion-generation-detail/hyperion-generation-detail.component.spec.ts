import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
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

    it('refreshes until a completed generation disappears', async () => {
        vi.useFakeTimers();
        service.getGenerationSandboxes.mockReturnValueOnce(of(sessions)).mockReturnValueOnce(of([]));
        fixture.detectChanges();
        expect(service.getGenerationSandboxes).toHaveBeenCalledTimes(1);

        await vi.advanceTimersByTimeAsync(5000);

        expect(service.getGenerationSandboxes).toHaveBeenCalledTimes(2);
        expect(fixture.componentInstance.notFound()).toBe(true);
        fixture.componentInstance.ngOnDestroy();
        vi.useRealTimers();
    });

    it('confirms cancellation and refreshes the job', () => {
        fixture.componentInstance.ngOnInit();
        const confirmationService = fixture.debugElement.injector.get(ConfirmationService);
        const confirm = vi.spyOn(confirmationService, 'confirm');
        service.getGenerationSandboxes.mockReturnValueOnce(of([]));

        fixture.componentInstance.confirmCancel();
        confirm.mock.calls[0][0].accept?.();

        expect(service.cancelGeneration).toHaveBeenCalledWith(42, 'job-1');
        expect(service.getGenerationSandboxes).toHaveBeenCalledTimes(2);
        expect(fixture.componentInstance.notFound()).toBe(true);
    });
});
