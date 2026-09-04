import { ComponentFixture, TestBed } from '@angular/core/testing';
import { computed, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { HyperionJobsIndicatorComponent } from 'app/hyperion/jobs-indicator/hyperion-jobs-indicator.component';
import {
    HyperionJobEntry,
    HyperionJobIndicatorState,
    HyperionJobRegistryService,
    HyperionJobStatus,
    isTerminalHyperionJobStatus,
} from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';

function entry(jobId: string, status: HyperionJobStatus, seen = false, overrides: Partial<HyperionJobEntry> = {}): HyperionJobEntry {
    return { jobId, exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting Algorithms', mode: 'GENERATE', startedAt: '2026-07-10T20:00:00Z', status, seen, ...overrides };
}

/** An ISO timestamp that many minutes in the past, for the rows that count against the wall clock. */
function minutesAgo(minutes: number): string {
    return new Date(Date.now() - minutes * 60_000).toISOString();
}

/** Stands in for the registry with the same signal surface, so the component can be driven directly. */
class StubRegistry {
    readonly writableEntries = signal<HyperionJobEntry[]>([]);
    readonly writableIndicatorState = signal<HyperionJobIndicatorState>('idle');
    readonly writableLoadFailed = signal(false);

    readonly entries = this.writableEntries.asReadonly();
    readonly indicatorState = this.writableIndicatorState.asReadonly();
    readonly loadFailed = this.writableLoadFailed.asReadonly();
    readonly unseenCount = computed(() => this.writableEntries().filter((item) => isTerminalHyperionJobStatus(item.status) && !item.seen).length);

    readonly markSeen = vi.fn((jobId: string) => this.writableEntries.update((items) => items.map((item) => (item.jobId === jobId ? { ...item, seen: true } : item))));
    readonly dismiss = vi.fn((jobId: string) => this.writableEntries.update((items) => items.filter((item) => item.jobId !== jobId)));
    readonly refresh = vi.fn();
    readonly track = vi.fn();
}

describe('HyperionJobsIndicatorComponent', () => {
    let fixture: ComponentFixture<HyperionJobsIndicatorComponent>;
    let registry: StubRegistry;

    /** Opens the tray and returns the overlay panel, which the CDK portals into the document body. */
    function openTray(): void {
        const trigger = document.querySelector<HTMLButtonElement>('[data-testid="hyperion-jobs-indicator-trigger"]');
        trigger!.click();
        fixture.detectChanges();
    }

    beforeEach(async () => {
        registry = new StubRegistry();
        await TestBed.configureTestingModule({
            imports: [HyperionJobsIndicatorComponent],
            providers: [
                // A catch-all route so clicking an entry link resolves instead of rejecting; the real generation
                // route is registered by the course-management routing module.
                provideRouter([{ path: '**', children: [] }]),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: HyperionJobRegistryService, useValue: registry },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(HyperionJobsIndicatorComponent);
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.destroy();
        vi.restoreAllMocks();
    });

    it('renders nothing at all while there is nothing to report', () => {
        expect(fixture.nativeElement.textContent.trim()).toBe('');
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-jobs-indicator-trigger"]')).toBeNull();
    });

    it('shows the trigger once a run is worth reporting', () => {
        registry.writableEntries.set([entry('j1', 'running')]);
        registry.writableIndicatorState.set('running');
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-jobs-indicator-trigger"]')).not.toBeNull();
    });

    it('badges only the finished runs the user has not opened', () => {
        registry.writableEntries.set([entry('j1', 'running')]);
        registry.writableIndicatorState.set('running');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-jobs-indicator-badge"]')).toBeNull();

        registry.writableEntries.set([entry('j1', 'failed')]);
        registry.writableIndicatorState.set('attention');
        fixture.detectChanges();
        const badge = fixture.nativeElement.querySelector('[data-testid="hyperion-jobs-indicator-badge"]');
        expect(badge).not.toBeNull();
        expect(badge.textContent).toContain('1');

        registry.writableEntries.set([entry('j1', 'failed', true)]);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-jobs-indicator-badge"]')).toBeNull();
    });

    it('links each run to its generation page and marks it seen when opened', () => {
        registry.writableEntries.set([entry('j1', 'failed')]);
        registry.writableIndicatorState.set('attention');
        fixture.detectChanges();

        openTray();
        const link = document.querySelector<HTMLAnchorElement>('[data-testid="hyperion-jobs-indicator-entry"]');
        expect(link).not.toBeNull();
        expect(link!.getAttribute('href')).toBe('/course-management/7/programming-exercises/42/generation');
        expect(link!.textContent).toContain('Sorting Algorithms');

        link!.click();
        fixture.detectChanges();

        expect(registry.markSeen).toHaveBeenCalledExactlyOnceWith('j1');
        expect(document.querySelector('[data-testid="hyperion-jobs-indicator-entry"]')).toBeNull();
    });

    it('removes an entry when it is dismissed', () => {
        registry.writableEntries.set([entry('j1', 'failed'), entry('j2', 'saved')]);
        registry.writableIndicatorState.set('attention');
        fixture.detectChanges();

        openTray();
        expect(document.querySelectorAll('[data-testid="hyperion-jobs-indicator-entry"]')).toHaveLength(2);

        document.querySelectorAll<HTMLButtonElement>('[data-testid="hyperion-jobs-indicator-dismiss"]')[0].click();
        fixture.detectChanges();

        expect(registry.dismiss).toHaveBeenCalledExactlyOnceWith('j1');
        expect(registry.entries()).toHaveLength(1);
        expect(document.querySelectorAll('[data-testid="hyperion-jobs-indicator-entry"]')).toHaveLength(1);
    });

    it('states explicitly when there is nothing to list, and says where a run would come from', () => {
        registry.writableIndicatorState.set('running');
        fixture.detectChanges();

        openTray();

        const empty = document.querySelector('[data-testid="hyperion-jobs-indicator-empty"]');
        expect(empty).not.toBeNull();
        expect(empty!.textContent).toContain('artemisApp.hyperion.jobs.empty');
        expect(empty!.textContent).toContain('artemisApp.hyperion.jobs.emptyHint');
    });

    it('presents the runs as a named list whose rows carry their own state', () => {
        registry.writableEntries.set([entry('j1', 'failed')]);
        registry.writableIndicatorState.set('attention');
        fixture.detectChanges();

        openTray();
        const list = document.querySelector('[role="list"]')!;

        // A named list, so a screen-reader user has something to choose between; the state is on the row for CSS and E2E.
        expect(list.getAttribute('aria-label')).toBe('artemisApp.hyperion.jobs.title');
        expect(list.querySelector('li')?.getAttribute('data-state')).toBe('failed');
        expect(document.querySelector('[data-testid="hyperion-jobs-indicator-entry"]')?.tagName).toBe('A');
    });

    it('puts the aggregate state into the visible label and the accessible name, not only into the dot', () => {
        registry.writableEntries.set([entry('j1', 'running'), entry('j2', 'failed'), entry('j3', 'saved')]);
        registry.writableIndicatorState.set('attention');
        fixture.detectChanges();

        const trigger = fixture.nativeElement.querySelector('[data-testid="hyperion-jobs-indicator-trigger"]');
        const summary = fixture.nativeElement.querySelector('[data-testid="hyperion-jobs-indicator-summary"]').textContent;

        // One running, one unseen failure to check, one unseen clean save: three counts, in that order.
        expect(summary).toContain('artemisApp.hyperion.jobs.summaryRunning');
        expect(summary).toContain('artemisApp.hyperion.jobs.summaryAttention');
        expect(summary).toContain('artemisApp.hyperion.jobs.summaryFinished');
        // A failed run and a running one must not be the same button to a screen reader.
        expect(trigger.getAttribute('aria-label')).toContain('artemisApp.hyperion.jobs.ariaLabel');
        expect(trigger.getAttribute('aria-label')).toContain('artemisApp.hyperion.jobs.summaryAttention');
    });

    it('falls back to the plain noun when the debounced indicator briefly outlives its entries', () => {
        registry.writableIndicatorState.set('running');
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-jobs-indicator-summary"]').textContent).toContain('artemisApp.hyperion.jobs.label');
    });

    it('counts a running run against the clock and freezes a finished one at the event that ended it', () => {
        registry.writableEntries.set([
            entry('j1', 'running', false, { startedAt: minutesAgo(7) }),
            entry('j2', 'failed', false, { startedAt: '2026-07-10T20:00:00Z', endedAt: '2026-07-10T20:21:00Z' }),
        ]);
        registry.writableIndicatorState.set('attention');
        fixture.detectChanges();

        openTray();
        const rows = document.querySelectorAll('[data-testid="hyperion-jobs-indicator-entry"]');

        expect(rows[0].textContent).toContain('artemisApp.hyperion.jobs.runningMinutes');
        // The finished run is measured against the server's own last event, so it does not count on for ever.
        expect(rows[1].textContent).toContain('artemisApp.hyperion.jobs.ranMinutes');
    });

    it('claims no duration at all for a run whose ending it never saw', () => {
        registry.writableEntries.set([entry('j1', 'unknown')]);
        registry.writableIndicatorState.set('attention');
        fixture.detectChanges();

        openTray();
        const row = document.querySelector('[data-testid="hyperion-jobs-indicator-entry"]')!;

        expect(row.textContent).toContain('artemisApp.hyperion.generation.status.unknown');
        expect(row.textContent).not.toContain('artemisApp.hyperion.jobs.ran');
    });

    it('states explicitly when the runs could not be loaded and offers a retry', () => {
        registry.writableEntries.set([entry('j1', 'running')]);
        registry.writableIndicatorState.set('running');
        registry.writableLoadFailed.set(true);
        fixture.detectChanges();

        openTray();
        expect(document.querySelector('[data-testid="hyperion-jobs-indicator-load-failed"]')).not.toBeNull();

        document.querySelector<HTMLButtonElement>('[data-testid="hyperion-jobs-indicator-retry"]')!.click();

        expect(registry.refresh).toHaveBeenCalledOnce();
    });
});
