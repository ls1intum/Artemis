import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HyperionGenerationJobsTableComponent } from './hyperion-generation-jobs-table.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import dayjs from 'dayjs/esm';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { Component, viewChild } from '@angular/core';

@Component({
    imports: [HyperionGenerationJobsTableComponent],
    template: '<jhi-hyperion-generation-jobs-table [jobs]="[]" />',
})
class TestHostComponent {
    readonly table = viewChild.required(HyperionGenerationJobsTableComponent);
}

describe('HyperionGenerationJobsTableComponent', () => {
    let fixture: ComponentFixture<HyperionGenerationJobsTableComponent>;
    let serverDateService: ArtemisServerDateService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HyperionGenerationJobsTableComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
        });
        serverDateService = TestBed.inject(ArtemisServerDateService);
        fixture = TestBed.createComponent(HyperionGenerationJobsTableComponent);
        fixture.componentRef.setInput('jobs', [
            {
                jobId: 'job-1',
                exerciseId: 42,
                exerciseTitle: 'Concurrency Lab',
                courseId: 7,
                userLogin: 'instructor',
                mode: 'GENERATE',
                startedAt: '2026-07-12T09:00:00Z',
                lastActivityAt: '2026-07-12T09:01:00Z',
                sessionId: 'agent-1::container-1',
                agentName: 'agent-1',
            },
        ]);
        fixture.componentRef.setInput('showAgent', true);
        fixture.detectChanges();
    });

    afterEach(() => vi.useRealTimers());

    it('renders localized operational data and a stable detail link', () => {
        const text = fixture.nativeElement.textContent;
        const detailLink = fixture.nativeElement.querySelector('[data-testid="hyperion-generation-details"]');

        expect(text).toContain('artemisApp.buildAgents.generationSandboxes.generate');
        expect(text).not.toContain('GENERATE');
        expect(text).toContain('agent-1');
        expect(text).toContain('Concurrency Lab');
        expect(text).not.toContain('artemisApp.buildAgents.generationSandboxes.slots');
        expect(detailLink.getAttribute('href')).toContain('/admin/hyperion-generations/job-1?agentName=agent-1');
    });

    it('presents the last sandbox activity as a self-updating relative time', () => {
        fixture.componentRef.setInput('jobs', [{ ...fixture.componentInstance.jobs()[0], lastActivityAt: dayjs().subtract(5, 'minutes').toISOString() }]);
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('5 minutes ago');
    });

    it('uses the server-adjusted clock for elapsed time', async () => {
        vi.useFakeTimers();
        vi.spyOn(serverDateService, 'now').mockReturnValue(dayjs('2026-07-12T09:01:00Z'));
        fixture.destroy();
        const hostFixture = TestBed.createComponent(TestHostComponent);
        hostFixture.detectChanges();

        await vi.advanceTimersByTimeAsync(1000);

        expect(hostFixture.componentInstance.table().elapsedSeconds('2026-07-12T09:00:00Z')).toBe(60);
    });

    it('does not present stale timing or a broken detail link as live data', () => {
        fixture.componentRef.setInput('jobs', [
            {
                ...fixture.componentInstance.jobs()[0],
                stale: true,
            },
        ]);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-details"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-duration-unavailable"]').textContent).toContain(
            'artemisApp.buildAgents.generationSandboxes.unavailable',
        );
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-details-unavailable"]').textContent).toContain(
            'artemisApp.buildAgents.generationSandboxes.liveDetailsUnavailable',
        );
    });
});
