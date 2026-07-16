import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EMPTY, Observable, Subject, map, of, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { ConfirmationService } from 'primeng/api';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionGenerationActivityComponent } from 'app/hyperion/exercise-generation/hyperion-generation-activity.component';
import { AlertService } from 'app/foundation/service/alert.service';
import {
    ExerciseGenerationFileSnapshot,
    ExerciseGenerationRevertResult,
    HyperionGenerationEvent,
    HyperionGenerationMessage,
    HyperionGenerationStatus,
} from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

type TestGenerationEvent = Omit<HyperionGenerationEvent, 'timestamp'> & { timestamp?: string };
type TestGenerationMessage = TestGenerationEvent | ExerciseGenerationFileSnapshot;
type TestGenerationStatus = Omit<HyperionGenerationStatus, 'events' | 'revertAvailable' | 'ownedByCaller' | 'cancellable'> & {
    events: TestGenerationEvent[];
    revertAvailable?: boolean;
    ownedByCaller?: boolean;
    cancellable?: boolean;
};

function normalizeEvent(event: TestGenerationEvent): HyperionGenerationEvent {
    return { ...event, timestamp: event.timestamp ?? '' };
}

function normalizeMessage(message: TestGenerationMessage): HyperionGenerationMessage {
    return message.type === 'FILE_SNAPSHOT' ? message : normalizeEvent(message);
}

function normalizeStatus(status: TestGenerationStatus): HyperionGenerationStatus {
    return {
        ...status,
        events: status.events.map(normalizeEvent),
        revertAvailable: status.revertAvailable ?? false,
        ownedByCaller: status.ownedByCaller ?? true,
        cancellable: status.cancellable ?? status.running,
    };
}

class MockService {
    status: TestGenerationStatus | null = null;
    stream$ = new Subject<TestGenerationMessage>();
    cancelCalls: [number, string][] = [];

    getStatus() {
        return of(new HttpResponse<HyperionGenerationStatus>({ body: this.status ? normalizeStatus(this.status) : null }));
    }

    cancel(exerciseId: number, jobId: string): Observable<void> {
        this.cancelCalls.push([exerciseId, jobId]);
        return of(undefined);
    }

    revertCalls: number[] = [];

    revertExerciseGeneration(exerciseId: number): Observable<ExerciseGenerationRevertResult> {
        this.revertCalls.push(exerciseId);
        return of({ fullyReverted: true, revertedRepositories: ['exercise', 'solution', 'tests'], completedAt: '2026-07-10T20:00:00Z' });
    }

    subscribeToStream(): Observable<HyperionGenerationMessage> {
        return this.stream$.pipe(map(normalizeMessage));
    }
}

function snapshot(path: string, action: 'create' | 'edit', content: string, overrides: Partial<ExerciseGenerationFileSnapshot> = {}): ExerciseGenerationFileSnapshot {
    const repo = path.startsWith('solution/') ? 'solution' : path.startsWith('template/') ? 'template' : path.startsWith('tests/') ? 'tests' : 'other';
    return { type: 'FILE_SNAPSHOT', path, repo, action, content, sha256: 'x', bytes: content.length, truncated: false, turn: 1, timestamp: '', ...overrides };
}

describe('HyperionGenerationActivityComponent', () => {
    let service: MockService;

    beforeEach(() => {
        vi.useRealTimers();
        service = new MockService();
        TestBed.configureTestingModule({
            imports: [HyperionGenerationActivityComponent],
            providers: [
                { provide: HyperionExerciseGenerationService, useValue: service },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    function createWith(status: TestGenerationStatus | null) {
        service.status = status;
        const fixture = TestBed.createComponent(HyperionGenerationActivityComponent);
        fixture.componentRef.setInput('exerciseId', 42);
        fixture.detectChanges();
        return fixture;
    }

    it('shows an intentional idle state that can request generation', () => {
        const fixture = createWith(null);
        const startRequested = vi.fn();
        fixture.componentInstance.startRequested.subscribe(startRequested);

        expect(fixture.componentInstance.visible()).toBe(true);
        expect(fixture.componentInstance.statusLoading()).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-empty"]')).not.toBeNull();

        fixture.debugElement.query(By.css('[data-testid="hyperion-generation-start"]')).triggerEventHandler('onClick');
        expect(startRequested).toHaveBeenCalledOnce();
    });

    it('does not offer generation when the exercise is no longer eligible', () => {
        const fixture = createWith(null);
        const startRequested = vi.fn();
        fixture.componentInstance.startRequested.subscribe(startRequested);
        fixture.componentRef.setInput('startAllowed', false);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-start"]')).toBeNull();

        fixture.componentInstance.requestStart();
        expect(startRequested).not.toHaveBeenCalled();
    });

    it('shows why editing is locked while status is loading', () => {
        const pendingStatus = new Subject<HttpResponse<HyperionGenerationStatus>>();
        service.getStatus = vi.fn(() => pendingStatus);

        const fixture = createWith(null);

        expect(fixture.componentInstance.visible()).toBe(true);
        expect(fixture.nativeElement.textContent).toContain('generationActivity.checkingStatus');
        expect(fixture.nativeElement.querySelector('jhi-monaco-editor')).toBeNull();
    });

    it('bounds automatic status retries and exposes a manual retry', () => {
        vi.useFakeTimers();
        service.getStatus = vi.fn(() => throwError(() => new Error('temporary failure')));
        const fixture = createWith(null);

        expect(fixture.componentInstance.statusLoading()).toBe(true);
        expect(service.getStatus).toHaveBeenCalledOnce();

        vi.advanceTimersByTime(1_000);

        expect(service.getStatus).toHaveBeenCalledTimes(2);
        vi.advanceTimersByTime(2_000);

        expect(service.getStatus).toHaveBeenCalledTimes(3);
        expect(fixture.componentInstance.statusLoading()).toBe(false);
        expect(fixture.componentInstance.statusLoadFailed()).toBe(true);
        expect(fixture.componentInstance.visible()).toBe(true);
        vi.advanceTimersByTime(60_000);
        expect(service.getStatus).toHaveBeenCalledTimes(3);

        fixture.debugElement.query(By.css('p-button')).triggerEventHandler('onClick');
        expect(service.getStatus).toHaveBeenCalledTimes(4);
        expect(fixture.componentInstance.statusLoading()).toBe(true);
    });

    it('releases the manual editor lock when retained-status requests never respond', () => {
        vi.useFakeTimers();
        service.getStatus = vi.fn(() => new Subject<HttpResponse<HyperionGenerationStatus>>());
        const fixture = createWith(null);

        vi.advanceTimersByTime(18_000);

        expect(service.getStatus).toHaveBeenCalledTimes(3);
        expect(fixture.componentInstance.statusLoading()).toBe(false);
        expect(fixture.componentInstance.statusLoadFailed()).toBe(true);
    });

    it('clears a status-load failure when the live stream succeeds', () => {
        vi.useFakeTimers();
        const fixture = createWith(null);
        service.getStatus = vi.fn(() => throwError(() => new Error('temporary failure')));
        fixture.componentInstance.attachToJob('j1', 'ADAPT');
        vi.advanceTimersByTime(3_000);
        expect(fixture.componentInstance.statusLoadFailed()).toBe(true);

        service.stream$.next({
            type: 'DONE',
            verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 1, reasons: [] },
            liveExerciseChanged: true,
        });

        expect(fixture.componentInstance.statusLoadFailed()).toBe(false);
        expect(fixture.componentInstance.statusLoading()).toBe(false);
    });

    it('ignores a late status error after a terminal live event', () => {
        const fixture = createWith(null);
        const pendingStatus = new Subject<HttpResponse<HyperionGenerationStatus>>();
        service.getStatus = vi.fn(() => pendingStatus);
        fixture.componentInstance.attachToJob('j1', 'ADAPT');

        service.stream$.next({
            type: 'DONE',
            verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 1, reasons: [] },
            liveExerciseChanged: true,
        });
        pendingStatus.error(new Error('late failure'));

        expect(fixture.componentInstance.statusLoadFailed()).toBe(false);
        expect(fixture.componentInstance.running()).toBe(false);
    });

    it('does not update status after the component is destroyed', () => {
        vi.useFakeTimers();
        const pendingStatus = new Subject<HttpResponse<HyperionGenerationStatus>>();
        service.getStatus = vi.fn(() => pendingStatus);
        const fixture = createWith(null);
        const component = fixture.componentInstance;

        fixture.destroy();
        pendingStatus.error(new Error('late status failure'));
        vi.advanceTimersByTime(60_000);

        expect(service.getStatus).toHaveBeenCalledOnce();
        expect(component.statusLoadFailed()).toBe(false);
    });

    it('rehydrates the changed-file inventory from retained status', () => {
        const fixture = createWith({
            jobId: 'j1',
            running: true,
            events: [{ type: 'STARTED', message: 'Starting' }],
            fileSnapshots: [snapshot('solution/A.java', 'create', 'a'), snapshot('tests/T.java', 'create', 't')],
        });
        const component = fixture.componentInstance;
        expect(component.visible()).toBe(true);
        expect(component.jobId()).toBe('j1');
        expect(component.snapshots()).toHaveLength(2);
        expect(component.filesByRepo().map((group) => group.repo)).toEqual(['solution', 'tests']);
        expect(fixture.nativeElement.querySelectorAll('[data-testid="hyperion-generation-file-static"]')).toHaveLength(2);
        expect(fixture.nativeElement.querySelector('jhi-monaco-editor')).toBeNull();
    });

    it('lets the instructor collapse and restore retained details', () => {
        const fixture = createWith({ jobId: 'j1', running: false, events: [], fileSnapshots: [snapshot('solution/A.java', 'create', 'a')] });
        const toggle = fixture.nativeElement.querySelector('[data-testid="hyperion-generation-details-toggle"]') as HTMLButtonElement;

        expect(toggle.getAttribute('aria-expanded')).toBe('true');
        expect(toggle.textContent).toContain('generationActivity.hideChangedFiles');
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-details"]')).not.toBeNull();

        toggle.click();
        fixture.detectChanges();

        expect(toggle.getAttribute('aria-expanded')).toBe('false');
        expect(toggle.textContent).toContain('generationActivity.showChangedFiles');
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-details"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-details"]').hidden).toBe(true);
    });

    it('keeps open details mounted when a live run completes', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [snapshot('solution/A.java', 'create', 'a')] });
        const disclosure = fixture.nativeElement.querySelector('[data-testid="hyperion-generation-details-toggle"]') as HTMLButtonElement;
        disclosure.focus();

        service.stream$.next({
            type: 'DONE',
            completionStatus: 'SUCCESS',
            verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 2, reasons: [] },
            liveExerciseChanged: true,
        });
        fixture.detectChanges();

        expect(fixture.componentInstance.detailsExpanded()).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-details"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelectorAll('[data-testid="hyperion-generation-details-toggle"]')).toHaveLength(1);
        expect(document.activeElement).toBe(disclosure);
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-review"]')).not.toBeNull();
    });

    it('shows recent progress visually while announcing only the coarse running state', () => {
        const fixture = createWith({
            jobId: 'j1',
            running: true,
            events: Array.from({ length: 10 }, (_, index) => ({ type: 'PROGRESS' as const, message: `event ${index}`, timestamp: `2026-07-13T09:00:${index}Z` })),
            fileSnapshots: [],
        });
        const current = fixture.nativeElement.querySelector('[data-testid="hyperion-generation-live-status"]');

        expect(current.textContent).toContain('generationActivity.running');
        expect(current.textContent).not.toContain('event 9');
        const visibleProgress = fixture.nativeElement.querySelector('[data-testid="hyperion-generation-current-progress"]');
        expect(visibleProgress.textContent).toContain('event 9');
        expect(visibleProgress.getAttribute('role')).toBeNull();
        expect(visibleProgress.getAttribute('aria-live')).toBeNull();
        expect(fixture.nativeElement.textContent).toContain('event 8');
        expect(fixture.nativeElement.querySelector('[role="log"]')).toBeNull();
        expect(fixture.nativeElement.querySelectorAll('[role="status"]')).toHaveLength(1);
    });

    it('announces a terminal outcome instead of stale progress in one atomic status region', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [{ type: 'PROGRESS', message: 'Still editing' }], fileSnapshots: [] });

        service.stream$.next({ type: 'ERROR', message: 'Generation failed' });
        fixture.detectChanges();

        const statuses = fixture.nativeElement.querySelectorAll('[role="status"]');
        expect(statuses).toHaveLength(1);
        expect(statuses[0].getAttribute('aria-atomic')).toBe('true');
        expect(statuses[0].textContent).toContain('generationActivity.terminalStatus.ERROR');
        expect(statuses[0].textContent).toContain('generationActivity.persistence.failed');
        expect(statuses[0].textContent).not.toContain('Still editing');
        const terminalMessage = fixture.nativeElement.querySelector('[data-testid="hyperion-generation-terminal-message"]');
        expect(terminalMessage.textContent).toContain('Generation failed');
        expect(terminalMessage.tagName).toBe('DIV');
        expect(terminalMessage.getAttribute('role')).toBeNull();
        expect(terminalMessage.getAttribute('aria-live')).toBeNull();
        expect(terminalMessage.closest('p-message')).toBeNull();
    });

    it('announces the editor refresh before reporting the terminal result as ready', () => {
        const fixture = createWith({ jobId: 'j1', running: false, events: [{ type: 'DONE', liveExerciseChanged: true }], fileSnapshots: [] });

        fixture.componentRef.setInput('refreshingEditor', true);
        fixture.detectChanges();

        const status = fixture.nativeElement.querySelector('[role="status"]');
        expect(status.textContent).toContain('generationActivity.refreshingEditor');
        expect(status.textContent).not.toContain('generationActivity.persistence.saved');

        fixture.componentRef.setInput('refreshingEditor', false);
        fixture.detectChanges();
        expect(status.textContent).toContain('generationActivity.persistence.saved');
    });

    it('announces when the saved exercise could not be loaded into the editor', () => {
        const fixture = createWith({ jobId: 'j1', running: false, events: [{ type: 'DONE', liveExerciseChanged: true }], fileSnapshots: [] });
        const refreshRequested = vi.fn();
        fixture.componentInstance.editorRefreshRequested.subscribe(refreshRequested);

        fixture.componentRef.setInput('editorRefreshFailed', true);
        fixture.detectChanges();

        const status = fixture.nativeElement.querySelector('[role="status"]');
        expect(status.textContent).toContain('generationActivity.editorRefreshFailed');
        expect(status.textContent).toContain('generationActivity.persistence.saved');

        fixture.nativeElement.querySelector('[data-testid="hyperion-editor-refresh-retry"] button').click();
        expect(refreshRequested).toHaveBeenCalledOnce();
    });

    it('gives same-path files unique repository-qualified accessible names', () => {
        const fixture = createWith({
            jobId: 'j1',
            running: false,
            events: [{ type: 'DONE', liveExerciseChanged: true }],
            fileSnapshots: [snapshot('src/Main.java', 'edit', 'solution', { repo: 'solution' }), snapshot('src/Main.java', 'edit', 'template', { repo: 'template' })],
        });
        fixture.componentInstance.detailsExpanded.set(true);
        fixture.detectChanges();

        const buttons = [...fixture.nativeElement.querySelectorAll('[data-testid="hyperion-generation-file"] button')] as HTMLButtonElement[];
        expect(buttons.map((button) => button.getAttribute('aria-label'))).toEqual([
            'artemisApp.hyperion.generationActivity.repo.solution: src/Main.java',
            'artemisApp.hyperion.generationActivity.repo.template: src/Main.java',
        ]);
    });

    it('does not report retained terminal history as a newly completed generation', () => {
        service.status = { jobId: 'j1', running: false, events: [{ type: 'DONE', liveExerciseChanged: true }], fileSnapshots: [] };
        const fixture = TestBed.createComponent(HyperionGenerationActivityComponent);
        const completed = vi.fn();
        fixture.componentInstance.generationCompleted.subscribe(completed);

        fixture.componentRef.setInput('exerciseId', 42);
        fixture.detectChanges();

        expect(completed).not.toHaveBeenCalled();
    });

    it('does not show a disclosure when there is no hidden content', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [{ type: 'PROGRESS', message: 'Only current state' }], fileSnapshots: [] });

        expect(fixture.componentInstance.hasDetails()).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-details-toggle"]')).toBeNull();
    });

    it('does not manufacture a file preview before the first file arrives', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [] });

        expect(fixture.nativeElement.querySelector('jhi-monaco-editor')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-file"]')).toBeNull();
    });

    it('coalesces live snapshots by repository and path', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [snapshot('solution/A.java', 'create', 'a')] });
        const component = fixture.componentInstance;

        service.stream$.next(snapshot('solution/A.java', 'edit', 'a2', { turn: 2 }));
        expect(component.snapshots()).toHaveLength(1);
        expect(component.snapshots()[0].content).toBe('a2');

        service.stream$.next(snapshot('solution/A.java', 'edit', 'stale', { turn: 1 }));
        expect(component.snapshots()[0].content).toBe('a2');

        service.stream$.next(snapshot('template/B.java', 'create', 'b'));
        expect(component.snapshots()).toHaveLength(2);
    });

    it('sorts files by display path within each repository', () => {
        const fixture = createWith({
            jobId: 'j1',
            running: true,
            events: [],
            fileSnapshots: [snapshot('solution/Z.java', 'create', 'z'), snapshot('solution/A.java', 'create', 'a')],
        });

        expect(fixture.componentInstance.filesByRepo()[0].files.map((file) => file.path)).toEqual(['solution/A.java', 'solution/Z.java']);
    });

    it('keeps live changed-file controls non-actionable until the run is terminal', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [] });
        const component = fixture.componentInstance;
        const selected = vi.fn();
        component.snapshotSelected.subscribe(selected);

        service.stream$.next(snapshot('src/Main.java', 'create', 'template', { repo: 'template' }));
        service.stream$.next(snapshot('src/Main.java', 'create', 'solution', { repo: 'solution' }));
        fixture.detectChanges();

        expect(component.snapshots()).toHaveLength(2);
        expect(component.filesByRepo().map((group) => [group.repo, group.files.map((file) => file.content)])).toEqual([
            ['solution', ['solution']],
            ['template', ['template']],
        ]);

        expect(fixture.nativeElement.querySelectorAll('[data-testid="hyperion-generation-file"]')).toHaveLength(0);
        expect(fixture.nativeElement.querySelectorAll('[data-testid="hyperion-generation-file-static"]')).toHaveLength(2);
        expect(selected).not.toHaveBeenCalled();
    });

    it('emits a selected snapshot after the run reaches a persisted terminal state', () => {
        const fixture = createWith({
            jobId: 'j1',
            running: false,
            events: [{ type: 'DONE', message: 'Saved', completionStatus: 'SUCCESS', liveExerciseChanged: true }],
            fileSnapshots: [snapshot('solution/src/Main.java', 'edit', 'solution')],
        });
        const selected = vi.fn();
        fixture.componentInstance.snapshotSelected.subscribe(selected);

        fixture.detectChanges();
        fixture.componentInstance.detailsExpanded.set(true);
        fixture.detectChanges();
        const changedFile = fixture.nativeElement.querySelector('[data-testid="hyperion-generation-file"] button') as HTMLButtonElement;
        changedFile.click();

        expect(changedFile.disabled).toBe(false);
        expect(selected).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ repo: 'solution', path: 'solution/src/Main.java' }));
    });

    it('offers one history review action for each saved artifact type', () => {
        const fixture = createWith({
            jobId: 'job-42',
            running: false,
            events: [{ type: 'DONE', liveExerciseChanged: true }],
            fileSnapshots: [
                snapshot('problem-statement.md', 'edit', 'problem'),
                snapshot('solution/src/Main.java', 'edit', 'solution'),
                snapshot('solution/src/Helper.java', 'edit', 'helper'),
                snapshot('tests/src/MainTest.java', 'edit', 'tests'),
            ],
        });

        const actions = fixture.debugElement.queryAll(By.css('[data-testid="hyperion-generation-review-action"]'));
        expect(actions.map((action) => action.attributes['data-review-target'])).toEqual(['problem-statement', 'solution', 'tests']);
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-review"]')).not.toBeNull();
    });

    it('emits the saved artifact and exact job when review is requested', () => {
        const fixture = createWith({
            jobId: 'job-42',
            running: false,
            events: [{ type: 'DONE', liveExerciseChanged: true }],
            fileSnapshots: [snapshot('template/src/Main.java', 'edit', 'template')],
        });
        const requested = vi.fn();
        fixture.componentInstance.reviewRequested.subscribe(requested);

        fixture.debugElement.query(By.css('[data-review-target="template"]')).triggerEventHandler('onClick');

        expect(requested).toHaveBeenCalledExactlyOnceWith({ target: 'template', jobId: 'job-42' });
    });

    it('restores all review actions from a baseline-only retained status', () => {
        const fixture = createWith({
            jobId: 'job-42',
            mode: 'GENERATE',
            running: false,
            revertAvailable: true,
            revertJobId: 'job-42',
            revertMode: 'GENERATE',
            events: [],
            fileSnapshots: [],
        });
        const requested = vi.fn();
        fixture.componentInstance.reviewRequested.subscribe(requested);
        fixture.detectChanges();

        const actions = fixture.debugElement.queryAll(By.css('[data-testid="hyperion-generation-review-action"]'));
        expect(actions.map((action) => action.attributes['data-review-target'])).toEqual(['problem-statement', 'solution', 'template', 'tests']);

        fixture.debugElement.query(By.css('[data-review-target="solution"]')).triggerEventHandler('onClick');
        expect(requested).toHaveBeenCalledExactlyOnceWith({ target: 'solution', jobId: 'job-42' });
    });

    it('does not offer saved-change review before persistence succeeds', () => {
        const fixture = createWith({
            jobId: 'job-42',
            running: false,
            events: [{ type: 'DONE', completionStatus: 'PARTIAL', liveExerciseChanged: false }],
            fileSnapshots: [snapshot('solution/src/Main.java', 'edit', 'draft')],
        });

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-review"]')).toBeNull();
    });

    it('keeps snapshots non-actionable when a terminal run did not change the live exercise', () => {
        const fixture = createWith({
            jobId: 'j1',
            running: false,
            events: [{ type: 'DONE', message: 'Draft only', completionStatus: 'PARTIAL', liveExerciseChanged: false }],
            fileSnapshots: [snapshot('solution/src/Main.java', 'edit', 'draft')],
        });
        fixture.componentInstance.detailsExpanded.set(true);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-file"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-file-static"]')).not.toBeNull();
    });

    it.each([
        [{ running: true, events: [] }, 'persistence.workingCopy'],
        [{ running: false, events: [{ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: true }] }, 'persistence.saved'],
        [{ running: false, events: [{ type: 'DONE', completionStatus: 'PARTIAL', liveExerciseChanged: false }] }, 'persistence.partial'],
        [{ running: false, events: [{ type: 'DONE', completionStatus: 'NEEDS_REVIEW', liveExerciseChanged: false }] }, 'persistence.draft'],
        [{ running: false, events: [{ type: 'CANCELLED' }] }, 'persistence.cancelled'],
        [{ running: false, events: [{ type: 'ERROR' }] }, 'persistence.failed'],
    ])('shows the persistence state for %o', (state, labelKey) => {
        const fixture = createWith({ jobId: 'j1', fileSnapshots: [], ...state } as TestGenerationStatus);

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-persistence-state"]').textContent).toContain(labelKey);
    });

    it.each([
        [{ type: 'ERROR' as const }, 'GENERATE' as const],
        [{ type: 'CANCELLED' as const }, 'ADAPT' as const],
        [{ type: 'DONE' as const, completionStatus: 'PARTIAL' as const }, 'GENERATE' as const],
    ])('offers a safe retry after %o', (event, mode) => {
        const fixture = createWith({ jobId: 'j1', mode, running: false, events: [event], fileSnapshots: [] });
        const requested = vi.fn();
        fixture.componentInstance.startRequested.subscribe(requested);
        fixture.detectChanges();

        fixture.debugElement.query(By.css('[data-testid="hyperion-generation-run-again"]')).triggerEventHandler('onClick');
        expect(requested).toHaveBeenCalledExactlyOnceWith(mode);
    });

    it('does not offer a retry when the exercise is no longer eligible', () => {
        const fixture = createWith({ jobId: 'j1', mode: 'GENERATE', running: false, events: [{ type: 'ERROR' }], fileSnapshots: [] });
        fixture.componentRef.setInput('startAllowed', false);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-run-again"]')).toBeNull();
        expect(fixture.componentInstance.canRunAgain()).toBe(false);
    });

    it('keeps partial-recovery instructions visible when Run again is offered', () => {
        const fixture = createWith({
            jobId: 'j1',
            mode: 'GENERATE',
            running: false,
            events: [{ type: 'DONE', completionStatus: 'PARTIAL', message: 'Review branch hyperion/recovery-123 manually.' }],
            fileSnapshots: [],
        });
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-terminal-message"]').textContent).toContain('hyperion/recovery-123');
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-run-again"]')).not.toBeNull();
    });

    it('reconciles cancellation status a bounded number of times when the terminal stream event stalls', () => {
        vi.useFakeTimers();
        service.status = { jobId: 'j1', mode: 'GENERATE', running: true, events: [], fileSnapshots: [] };
        const fixture = createWith(service.status);
        const statusSpy = vi.spyOn(service, 'getStatus');

        fixture.componentInstance.cancel();
        vi.advanceTimersByTime(10_000);

        expect(statusSpy).toHaveBeenCalled();
        expect(fixture.componentInstance.cancelRequested()).toBe(true);

        const callsAfterInitialReconciliation = statusSpy.mock.calls.length;
        vi.advanceTimersByTime(10_000);
        expect(statusSpy.mock.calls.length).toBeGreaterThan(callsAfterInitialReconciliation);

        fixture.componentInstance.cancel();
        expect(service.cancelCalls).toEqual([[42, 'j1']]);
    });

    it('clears stale running state when cancellation status has no retained job', () => {
        vi.useFakeTimers();
        const fixture = createWith({ jobId: 'j1', mode: 'GENERATE', running: true, events: [], fileSnapshots: [] });
        service.status = null;

        fixture.componentInstance.cancel();
        vi.advanceTimersByTime(1_000);

        expect(fixture.componentInstance.cancelRequested()).toBe(false);
        expect(fixture.componentInstance.running()).toBe(false);
        expect(fixture.componentInstance.jobId()).toBeUndefined();
    });

    it('adopts a newer job when cancellation reconciliation returns a different job', () => {
        vi.useFakeTimers();
        const fixture = createWith({ jobId: 'j1', mode: 'GENERATE', running: true, events: [], fileSnapshots: [] });
        service.status = { jobId: 'j2', mode: 'ADAPT', running: true, events: [], fileSnapshots: [] };

        fixture.componentInstance.cancel();
        vi.advanceTimersByTime(1_000);

        expect(fixture.componentInstance.cancelRequested()).toBe(false);
        expect(fixture.componentInstance.jobId()).toBe('j2');
        expect(fixture.componentInstance.mode()).toBe('ADAPT');
        expect(fixture.componentInstance.running()).toBe(true);
    });

    it('uses generation-specific undo copy for a generated exercise', () => {
        const fixture = createWith({
            jobId: 'j1',
            mode: 'GENERATE',
            running: false,
            revertAvailable: true,
            revertJobId: 'j1',
            revertMode: 'GENERATE',
            events: [{ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: true }],
            fileSnapshots: [],
        });
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-revert"]').textContent).toContain('generationActivity.undoGeneration');
    });

    it('caps retained progress events to the latest entries', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [] });
        const component = fixture.componentInstance;

        for (let i = 0; i < 55; i++) {
            service.stream$.next({ type: 'PROGRESS', message: `event ${i}` });
        }

        expect(component.events()).toHaveLength(50);
        expect(component.events()[0]?.message).toBe('event 5');
        expect(component.events()[49]?.message).toBe('event 54');
    });

    it('caps retained status events to match live stream retention', () => {
        const fixture = createWith({
            jobId: 'j1',
            running: false,
            events: Array.from({ length: 55 }, (_, index) => ({ type: 'PROGRESS' as const, message: `event ${index}` })),
            fileSnapshots: [],
        });
        const component = fixture.componentInstance;

        expect(component.events()).toHaveLength(50);
        expect(component.events()[0]?.message).toBe('event 5');
        expect(component.events()[49]?.message).toBe('event 54');
    });

    it('records the terminal verdict from the stream', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [] });
        const component = fixture.componentInstance;
        const completed = vi.fn();
        component.generationCompleted.subscribe(completed);

        service.stream$.next({
            type: 'DONE',
            completionStatus: 'NEEDS_REVIEW',
            verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 5, reasons: [] },
        });
        fixture.detectChanges();

        expect(component.running()).toBe(false);
        expect(component.verdict()?.accepted).toBe(true);
        expect(component.completionStatus()).toBe('NEEDS_REVIEW');
        expect(completed).toHaveBeenCalledExactlyOnceWith({
            mode: undefined,
            verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 5, reasons: [] },
            completionStatus: 'NEEDS_REVIEW',
            liveExerciseChanged: undefined,
            completedAt: '',
        });
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-completion-status"]')).toBeNull();
    });

    it('reconciles retained snapshots when a terminal event overtakes an earlier file message', () => {
        const fixture = createWith({
            jobId: 'j1',
            mode: 'GENERATE',
            running: true,
            events: [],
            fileSnapshots: [snapshot('solution/A.java', 'create', 'old', { turn: 1 })],
        });
        service.status = {
            jobId: 'j1',
            mode: 'GENERATE',
            running: false,
            events: [{ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: true }],
            fileSnapshots: [snapshot('solution/A.java', 'edit', 'new', { turn: 2 })],
        };

        service.stream$.next({ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: true });

        expect(fixture.componentInstance.snapshots()).toHaveLength(1);
        expect(fixture.componentInstance.snapshots()[0].content).toBe('new');
    });

    it('describes the inverted template check as an expected pass condition', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [] });

        service.stream$.next({
            type: 'DONE',
            completionStatus: 'SUCCESS',
            verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 1, reasons: [] },
        });
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('verdict.templateFailedExpected');
        expect(fixture.nativeElement.textContent).toContain('verdict.oneTest');
    });

    it('surfaces the failed-gate reasons of a rejected verdict', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [] });

        service.stream$.next({
            type: 'ERROR',
            verdict: { accepted: false, solutionPassed: false, templateFailed: true, testCount: 2, reasons: ['solution failed 1 test', 'no gradable test'] },
        });
        fixture.detectChanges();

        const reasons = fixture.nativeElement.querySelector('[data-testid="hyperion-generation-verdict-reasons"]');
        expect(reasons?.textContent).toContain('solution failed 1 test');
        expect(reasons?.textContent).toContain('no gradable test');
        const text = fixture.nativeElement.textContent;
        expect(text).toContain('verdict.solutionFailed');
        expect(text).not.toContain('verdict.solutionPassed');
    });

    it('requests cancellation for the owner', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [] });
        fixture.componentInstance.cancel();
        expect(service.cancelCalls).toEqual([[42, 'j1']]);
        expect(fixture.componentInstance.cancelRequested()).toBe(true);
    });

    it('reports a cancellation request failure and refreshes status', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [] });
        const alertService = TestBed.inject(AlertService);
        const errorSpy = vi.spyOn(alertService, 'error');
        const statusSpy = vi.spyOn(service, 'getStatus');
        service.cancel = vi.fn(() => throwError(() => new HttpErrorResponse({ status: 503 })));

        fixture.componentInstance.cancel();

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.hyperion.generationActivity.cancelFailed');
        expect(statusSpy).toHaveBeenCalledWith(42);
        expect(fixture.componentInstance.cancelRequested()).toBe(false);
    });

    it('ignores late cancel and undo emissions after destruction', () => {
        vi.useFakeTimers();
        const fixture = createWith({
            jobId: 'j1',
            mode: 'ADAPT',
            running: false,
            revertAvailable: true,
            events: [{ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: true }],
            fileSnapshots: [],
        });
        const component = fixture.componentInstance;
        const statusSpy = vi.spyOn(service, 'getStatus');
        const cancel$ = new Subject<void>();
        const revert$ = new Subject<ExerciseGenerationRevertResult>();
        service.cancel = vi.fn(() => cancel$);
        service.revertExerciseGeneration = vi.fn(() => revert$);
        const reverted = vi.fn();
        component.generationReverted.subscribe(reverted);
        const alertService = TestBed.inject(AlertService);
        const errorSpy = vi.spyOn(alertService, 'error');
        const successSpy = vi.spyOn(alertService, 'success');

        (component as any).revert();
        component.running.set(true);
        component.cancel();
        const stateBeforeDestroy = { reverting: component.reverting(), cancelRequested: component.cancelRequested() };
        fixture.destroy();
        const timersAfterDestroy = vi.getTimerCount();

        cancel$.next();
        revert$.next({ fullyReverted: true, revertedRepositories: ['solution'], completedAt: '2026-07-11T12:00:00Z' });
        expect(vi.getTimerCount()).toBe(timersAfterDestroy);
        vi.advanceTimersByTime(60_000);

        expect({ reverting: component.reverting(), cancelRequested: component.cancelRequested() }).toEqual(stateBeforeDestroy);
        expect(statusSpy).not.toHaveBeenCalled();
        expect(errorSpy).not.toHaveBeenCalled();
        expect(successSpy).not.toHaveBeenCalled();
        expect(reverted).not.toHaveBeenCalled();
    });

    it('does not retry a late revert-availability failure after destruction', () => {
        vi.useFakeTimers();
        const fixture = createWith({ jobId: 'j1', mode: 'ADAPT', running: false, events: [], fileSnapshots: [] });
        const availability$ = new Subject<HttpResponse<HyperionGenerationStatus>>();
        service.getStatus = vi.fn(() => availability$);

        (fixture.componentInstance as any).refreshRevertAvailability(42, 'j1');
        fixture.destroy();
        availability$.error(new Error('late failure'));
        expect(vi.getTimerCount()).toBe(0);
        vi.advanceTimersByTime(60_000);

        expect(service.getStatus).toHaveBeenCalledOnce();
    });

    it('refreshes retained status when cancellation is rejected or already terminal', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [] });
        const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');
        service.cancel = (exerciseId: number, jobId: string) => {
            service.cancelCalls.push([exerciseId, jobId]);
            return throwError(() => new HttpErrorResponse({ status: 404 }));
        };
        service.status = {
            jobId: 'j1',
            running: false,
            mode: 'GENERATE',
            events: [{ type: 'CANCELLED', message: 'Generation was cancelled. Nothing was changed.' }],
            fileSnapshots: [],
        };

        fixture.componentInstance.cancel();

        expect(service.cancelCalls).toEqual([[42, 'j1']]);
        expect(fixture.componentInstance.cancelRequested()).toBe(false);
        expect(fixture.componentInstance.running()).toBe(false);
        expect(fixture.componentInstance.events()).toEqual([{ type: 'CANCELLED', message: 'Generation was cancelled. Nothing was changed.', timestamp: '' }]);
        expect(errorSpy).not.toHaveBeenCalled();
    });

    it('attaches to a freshly started adapt run and shows the adapting label', () => {
        const fixture = createWith(null);
        const component = fixture.componentInstance;

        component.attachToJob('j9', 'ADAPT');
        expect(component.visible()).toBe(true);
        expect(component.jobId()).toBe('j9');
        expect(component.running()).toBe(true);
        expect(component.runningLabelKey()).toBe('artemisApp.hyperion.generationActivity.adapting');
    });

    it('shows a sanitized active run owned by another instructor without exposing cancellation', () => {
        const subscribeToStream = vi.spyOn(service, 'subscribeToStream');
        const fixture = createWith({ jobId: 'other-job', mode: 'GENERATE', running: true, ownedByCaller: false, events: [], fileSnapshots: [] });
        fixture.detectChanges();

        expect(fixture.componentInstance.running()).toBe(true);
        expect(fixture.componentInstance.ownedByCaller()).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-cancel"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-live-status"]')?.textContent).toContain('runningByAnotherInstructor');
        expect(subscribeToStream).not.toHaveBeenCalled();

        fixture.componentInstance.cancel();
        expect(service.cancelCalls).toEqual([]);
    });

    it('requests an editor refresh before clearing a sanitized run that disappears', () => {
        vi.useFakeTimers();
        const fixture = createWith({ jobId: 'other-job', mode: 'GENERATE', running: true, ownedByCaller: false, events: [], fileSnapshots: [] });
        const completed = vi.fn(() => expect(fixture.componentInstance.running()).toBe(true));
        fixture.componentInstance.generationCompleted.subscribe(completed);
        expect(fixture.componentInstance.running()).toBe(true);

        service.status = null;
        vi.advanceTimersByTime(5_000);

        expect(completed).toHaveBeenCalledExactlyOnceWith({ mode: 'GENERATE', liveExerciseChanged: true });
        expect(fixture.componentInstance.running()).toBe(false);
        expect(fixture.componentInstance.jobId()).toBeUndefined();
    });

    it('treats missing ownership fields as non-owned and non-cancellable', () => {
        const malformedStatus = {
            jobId: 'unknown-owner-job',
            mode: 'GENERATE',
            running: true,
            events: [],
            fileSnapshots: [],
            revertAvailable: false,
        } as unknown as HyperionGenerationStatus;
        service.getStatus = vi.fn(() => of(new HttpResponse({ body: malformedStatus })));
        const subscribeToStream = vi.spyOn(service, 'subscribeToStream');

        const fixture = createWith(null);

        expect(fixture.componentInstance.ownedByCaller()).toBe(false);
        expect(fixture.componentInstance.cancellable()).toBe(false);
        expect(subscribeToStream).not.toHaveBeenCalled();
        fixture.componentInstance.cancel();
        expect(service.cancelCalls).toEqual([]);
    });

    it('subscribes before a second authoritative status read closes the reload race', () => {
        const running = normalizeStatus({ jobId: 'j1', mode: 'GENERATE', running: true, events: [], fileSnapshots: [] });
        const done = normalizeStatus({
            jobId: 'j1',
            mode: 'GENERATE',
            running: false,
            events: [{ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: true }],
            fileSnapshots: [],
        });
        service.getStatus = vi
            .fn()
            .mockReturnValueOnce(of(new HttpResponse({ body: running })))
            .mockReturnValueOnce(of(new HttpResponse({ body: done })));
        const subscribeToStream = vi.spyOn(service, 'subscribeToStream');

        const fixture = createWith(null);

        expect(subscribeToStream).toHaveBeenCalledOnce();
        expect(service.getStatus).toHaveBeenCalledTimes(2);
        expect(fixture.componentInstance.running()).toBe(false);
        expect(fixture.componentInstance.completionStatus()).toBe('SUCCESS');
    });

    it('hides cancellation while the owner run is finalizing', () => {
        const fixture = createWith({ jobId: 'j1', mode: 'GENERATE', running: true, ownedByCaller: true, cancellable: false, events: [], fileSnapshots: [] });
        fixture.detectChanges();

        expect(fixture.componentInstance.runningLabelKey()).toContain('finalizing');
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-cancel"]')).toBeNull();
        fixture.componentInstance.cancel();
        expect(service.cancelCalls).toEqual([]);
    });

    it('replays retained status immediately after attaching to avoid missing early stream events', () => {
        const fixture = createWith(null);
        const component = fixture.componentInstance;
        service.status = {
            jobId: 'j9',
            running: true,
            mode: 'GENERATE',
            events: [{ type: 'PROGRESS', message: 'already produced files' }],
            fileSnapshots: [snapshot('solution/A.java', 'create', 'a')],
        };

        component.attachToJob('j9', 'GENERATE');

        expect(component.events()).toEqual([{ type: 'PROGRESS', message: 'already produced files', timestamp: '' }]);
        expect(component.snapshots()).toHaveLength(1);
        expect(component.snapshots()[0].path).toBe('solution/A.java');
    });

    it('confirms undo for an accepted adaptation and leaves one truthful terminal state', () => {
        const fixture = createWith(null);
        const component = fixture.componentInstance;
        const reverted = vi.fn();
        component.generationReverted.subscribe(reverted);

        component.attachToJob('j9', 'ADAPT');
        service.status = {
            jobId: 'j9',
            running: false,
            mode: 'ADAPT',
            events: [],
            fileSnapshots: [],
            revertAvailable: true,
        };
        service.stream$.next(snapshot('solution/A.java', 'create', 'adapted'));
        expect(component.snapshots()[0].content).toBe('adapted');
        expect(component.canRevert()).toBe(false);

        service.stream$.next({
            type: 'DONE',
            completionStatus: 'SUCCESS',
            verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] },
            liveExerciseChanged: true,
        });
        expect(component.running()).toBe(false);
        expect(component.canRevert()).toBe(true);

        const confirm = vi.spyOn(fixture.debugElement.injector.get(ConfirmationService), 'confirm');
        fixture.detectChanges();
        fixture.debugElement.query(By.css('[data-testid="hyperion-generation-revert"]')).triggerEventHandler('onClick');
        expect(service.revertCalls).toEqual([]);
        const confirmation = confirm.mock.calls[0][0];
        expect(confirmation.defaultFocus).toBe('reject');
        confirmation.accept?.();

        expect(service.revertCalls).toEqual([42]);
        expect(component.reverted()).toBe(true);
        expect(component.jobId()).toBeUndefined();
        expect(component.mode()).toBe('ADAPT');
        expect(component.verdict()).toBeUndefined();
        expect(component.snapshots()).toEqual([]);
        expect(reverted).toHaveBeenCalledExactlyOnceWith('2026-07-10T20:00:00Z');
        expect(component.canRevert()).toBe(false);
    });

    it('replaces stale success details with a persistent warning when undo is partial', () => {
        const fixture = createWith(null);
        const component = fixture.componentInstance;
        const reverted = vi.fn();
        component.generationReverted.subscribe(reverted);
        service.revertExerciseGeneration = (exerciseId: number) => {
            service.revertCalls.push(exerciseId);
            return throwError(
                () => new HttpErrorResponse({ status: 409, error: { fullyReverted: false, revertedRepositories: ['TEMPLATE'], completedAt: '2026-07-10T20:00:00Z' } }),
            );
        };

        component.attachToJob('j9', 'ADAPT');
        service.status = {
            jobId: 'j9',
            running: false,
            mode: 'ADAPT',
            events: [],
            fileSnapshots: [],
            revertAvailable: true,
        };
        service.stream$.next(snapshot('solution/A.java', 'create', 'adapted'));
        service.stream$.next({
            type: 'DONE',
            completionStatus: 'SUCCESS',
            verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] },
            liveExerciseChanged: true,
        });

        const confirm = vi.spyOn(fixture.debugElement.injector.get(ConfirmationService), 'confirm');
        component.confirmRevert();
        confirm.mock.calls[0][0].accept?.();

        expect(service.revertCalls).toEqual([42]);
        expect(component.reverted()).toBe(false);
        expect(component.snapshots()).toHaveLength(0);
        expect(component.verdict()).toBeUndefined();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-revert-partial"]')).not.toBeNull();
        expect(reverted).not.toHaveBeenCalled();
        expect(component.canRevert()).toBe(true);
    });

    it('restores the adapt mode on reconnect so the revert affordance survives a reload', () => {
        const fixture = createWith({
            jobId: 'j5',
            running: false,
            mode: 'ADAPT',
            events: [
                {
                    type: 'DONE',
                    completionStatus: 'SUCCESS',
                    verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 4, reasons: [] },
                    liveExerciseChanged: true,
                },
            ],
            fileSnapshots: [],
            revertAvailable: true,
        });
        const component = fixture.componentInstance;

        expect(component.mode()).toBe('ADAPT');
        expect(component.runningLabelKey()).toBe('artemisApp.hyperion.generationActivity.adapting');
        expect(component.canRevert()).toBe(true);
    });

    it('does not offer revert for an accepted generate run', () => {
        const fixture = createWith(null);
        const component = fixture.componentInstance;

        component.attachToJob('j9', 'GENERATE');
        service.stream$.next({ type: 'DONE', verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] } });
        expect(component.canRevert()).toBe(false);
    });

    it('does not offer revert when an accepted adaptation was only partially saved', () => {
        const fixture = createWith(null);
        const component = fixture.componentInstance;

        component.attachToJob('j9', 'ADAPT');
        service.stream$.next({
            type: 'DONE',
            completionStatus: 'PARTIAL',
            verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] },
            liveExerciseChanged: false,
        });

        expect(component.canRevert()).toBe(false);
    });

    it('does not offer undo when the server no longer retains the adaptation baseline', () => {
        const fixture = createWith({
            jobId: 'j9',
            running: false,
            mode: 'ADAPT',
            events: [
                {
                    type: 'DONE',
                    completionStatus: 'SUCCESS',
                    verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] },
                    liveExerciseChanged: true,
                },
            ],
            fileSnapshots: [],
            revertAvailable: false,
        });

        expect(fixture.componentInstance.canRevert()).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-revert"]')).toBeNull();
    });

    it('keeps an authoritative undo available when a later run owns the retained transcript', () => {
        const fixture = createWith({
            jobId: 'later-run',
            running: false,
            mode: 'GENERATE',
            events: [{ type: 'CANCELLED', message: 'Cancelled' }],
            fileSnapshots: [],
            revertAvailable: true,
            revertJobId: 'successful-adaptation',
            revertMode: 'ADAPT',
        });

        expect(fixture.componentInstance.canRevert()).toBe(true);
        expect(fixture.componentInstance.undoLabelKey()).toBe('artemisApp.hyperion.generationActivity.undoAdaptation');
        const review = vi.fn();
        fixture.componentInstance.reviewRequested.subscribe(review);
        fixture.componentInstance.requestReview('solution');
        expect(review).toHaveBeenCalledWith({ target: 'solution', jobId: 'successful-adaptation' });

        const confirmation = vi.spyOn(fixture.debugElement.injector.get(ConfirmationService), 'confirm');
        const success = vi.spyOn(TestBed.inject(AlertService), 'success');
        fixture.componentInstance.confirmRevert();
        expect(confirmation.mock.calls[0][0].header).toContain('undoAdaptationConfirmHeader');
        expect(confirmation.mock.calls[0][0].message).toContain('undoAdaptationConfirmMessage');
        confirmation.mock.calls[0][0].accept?.();
        expect(success).toHaveBeenCalledWith('artemisApp.hyperion.generationActivity.undoAdaptationSuccess');
        expect(fixture.componentInstance.undoneLabelKey()).toBe('artemisApp.hyperion.generationActivity.adaptationUndone');
    });

    it('shows a retained undo without manufacturing empty activity details', () => {
        const fixture = createWith({
            jobId: 'j9',
            running: false,
            mode: 'ADAPT',
            events: [],
            fileSnapshots: [],
            revertAvailable: true,
            revertJobId: 'j9',
            revertMode: 'ADAPT',
        });

        expect(fixture.componentInstance.canRevert()).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-revert"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-details-toggle"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-details"]')).toBeNull();
    });

    it('refreshes authoritative status instead of pretending completion when the live stream errors', () => {
        vi.useFakeTimers();
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [] });
        const component = fixture.componentInstance;
        service.status = { jobId: 'j1', running: true, events: [{ type: 'PROGRESS', message: 'still running' }], fileSnapshots: [] };
        expect(component.running()).toBe(true);

        service.stream$.error(new Error('ws dropped'));
        vi.advanceTimersByTime(1_000);
        expect(component.running()).toBe(true);
        expect(component.events()).toEqual([{ type: 'PROGRESS', message: 'still running', timestamp: '' }]);
    });

    it('periodically reconciles an active stream so a lost terminal message cannot leave the editor stuck', () => {
        vi.useFakeTimers();
        const fixture = createWith({ jobId: 'j1', mode: 'GENERATE', running: true, events: [], fileSnapshots: [] });
        const component = fixture.componentInstance;
        const completed = vi.fn();
        component.generationCompleted.subscribe(completed);
        service.status = null;
        vi.advanceTimersByTime(5_000);
        expect(component.running()).toBe(true);

        service.status = {
            jobId: 'j1',
            mode: 'GENERATE',
            running: false,
            events: [{ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: true }],
            fileSnapshots: [],
        };

        vi.advanceTimersByTime(5_000);

        expect(component.running()).toBe(false);
        expect(component.completionStatus()).toBe('SUCCESS');
        expect(completed).toHaveBeenCalledExactlyOnceWith({
            mode: 'GENERATE',
            verdict: undefined,
            completionStatus: 'SUCCESS',
            liveExerciseChanged: true,
            completedAt: '',
        });
    });

    it('replaces a stale local snapshot with a newer retained status snapshot after stream loss', () => {
        vi.useFakeTimers();
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [snapshot('solution/A.java', 'create', 'old', { turn: 1 })] });
        const component = fixture.componentInstance;
        service.status = {
            jobId: 'j1',
            running: true,
            events: [{ type: 'PROGRESS', message: 'status caught up' }],
            fileSnapshots: [snapshot('solution/A.java', 'edit', 'new', { turn: 2 })],
        };

        service.stream$.error(new Error('ws dropped'));
        vi.advanceTimersByTime(1_000);

        expect(component.snapshots()).toHaveLength(1);
        expect(component.snapshots()[0].content).toBe('new');
    });

    it('polls status instead of staying stuck when the stream completes without an event', () => {
        vi.useFakeTimers();
        service.subscribeToStream = () => EMPTY;
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [] });
        const component = fixture.componentInstance;
        const completed = vi.fn();
        component.generationCompleted.subscribe(completed);
        service.status = {
            jobId: 'j1',
            running: false,
            events: [{ type: 'DONE', verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] }, liveExerciseChanged: true }],
            fileSnapshots: [],
        };

        vi.advanceTimersByTime(1_000);

        expect(component.running()).toBe(false);
        expect(component.verdict()?.accepted).toBe(true);
        expect(completed).toHaveBeenCalledExactlyOnceWith({
            mode: undefined,
            verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] },
            completionStatus: undefined,
            liveExerciseChanged: true,
            completedAt: '',
        });
    });

    it('stops running on a CANCELLED terminal event from the stream', () => {
        const fixture = createWith({ jobId: 'j1', running: true, mode: 'ADAPT', events: [], fileSnapshots: [] });
        const component = fixture.componentInstance;

        service.stream$.next({ type: 'CANCELLED', message: 'Cancelled by user' });
        expect(component.running()).toBe(false);
        expect(component.verdict()).toBeUndefined();
        expect(component.canRevert()).toBe(false);
    });

    it('refreshes a prior adaptation undo after a later generation stops', () => {
        vi.useFakeTimers();
        const fixture = createWith(null);
        const component = fixture.componentInstance;
        const runningStatus: HyperionGenerationStatus = {
            jobId: 'later-run',
            running: true,
            mode: 'GENERATE',
            events: [],
            fileSnapshots: [],
            revertAvailable: false,
            ownedByCaller: true,
            cancellable: true,
        };
        const stoppedStatus = { ...runningStatus, running: false, events: [{ type: 'CANCELLED' as const, message: 'Cancelled' }] };
        service.getStatus = vi
            .fn()
            .mockReturnValueOnce(of(new HttpResponse({ body: runningStatus })))
            .mockReturnValueOnce(of(new HttpResponse({ body: stoppedStatus })))
            .mockReturnValueOnce(of(new HttpResponse({ body: { ...stoppedStatus, revertAvailable: true } })));
        component.attachToJob('later-run', 'GENERATE');

        service.stream$.next({ type: 'CANCELLED', message: 'Cancelled' });

        expect(component.canRevert()).toBe(true);
        expect(service.getStatus).toHaveBeenCalledTimes(3);
    });

    it.each([
        ['CANCELLED' as const, 'terminalStatus.CANCELLED'],
        ['ERROR' as const, 'terminalStatus.ERROR'],
    ])('keeps the %s outcome visible when details are collapsed', (type, labelKey) => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [] });
        service.stream$.next({ type, message: 'terminal' });
        fixture.componentInstance.detailsExpanded.set(false);
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain(labelKey);
    });

    it.each([
        [
            {
                type: 'DONE' as const,
                completionStatus: 'SUCCESS' as const,
                verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] },
                liveExerciseChanged: true,
            },
            true,
        ],
        [
            { type: 'DONE' as const, completionStatus: 'PARTIAL' as const, verdict: { accepted: false, solutionPassed: false, templateFailed: true, testCount: 3, reasons: [] } },
            false,
        ],
        [{ type: 'CANCELLED' as const, message: 'Cancelled' }, false],
        [{ type: 'ERROR' as const, message: 'Failed' }, false],
    ])('rehydrates terminal status %s from retained status', (terminalEvent, canRevert) => {
        const fixture = createWith({
            jobId: 'j1',
            running: false,
            mode: 'ADAPT',
            events: [terminalEvent],
            fileSnapshots: [snapshot('solution/A.java', 'create', 'a')],
            revertAvailable: canRevert,
        });
        const component = fixture.componentInstance;

        expect(component.running()).toBe(false);
        expect(component.detailsExpanded()).toBe(false);
        expect(component.events().at(-1)).toEqual({ ...terminalEvent, timestamp: '' });
        expect(component.canRevert()).toBe(canRevert);
        if ('completionStatus' in terminalEvent) {
            expect(component.completionStatus()).toBe(terminalEvent.completionStatus);
        }
    });

    it('does not let a late status response clobber a freshly attached live run', () => {
        const pendingStatus = new Subject<HttpResponse<HyperionGenerationStatus>>();
        service.getStatus = () => pendingStatus.asObservable();
        const fixture = createWith(null);
        const component = fixture.componentInstance;

        component.attachToJob('live', 'GENERATE');
        expect(component.jobId()).toBe('live');
        expect(component.running()).toBe(true);

        pendingStatus.next(new HttpResponse<HyperionGenerationStatus>({ body: normalizeStatus({ jobId: 'stale', running: false, events: [], fileSnapshots: [] }) }));
        expect(component.jobId()).toBe('live');
        expect(component.running()).toBe(true);
    });

    it('adopts a newer active run owned by the same instructor', () => {
        const pendingStatus = new Subject<HttpResponse<HyperionGenerationStatus>>();
        service.getStatus = () => pendingStatus.asObservable();
        const fixture = createWith(null);
        const component = fixture.componentInstance;

        component.attachToJob('previous', 'GENERATE');
        pendingStatus.next(
            new HttpResponse<HyperionGenerationStatus>({
                body: normalizeStatus({ jobId: 'current', mode: 'ADAPT', running: true, ownedByCaller: true, events: [], fileSnapshots: [] }),
            }),
        );

        expect(component.jobId()).toBe('current');
        expect(component.mode()).toBe('ADAPT');
        expect(component.running()).toBe(true);
    });

    it('discovers a run started after this editor became idle', () => {
        vi.useFakeTimers();
        const fixture = createWith(null);
        const component = fixture.componentInstance;
        service.status = { jobId: 'remote', mode: 'ADAPT', running: true, ownedByCaller: false, events: [], fileSnapshots: [] };

        vi.advanceTimersByTime(15_000);

        expect(component.jobId()).toBe('remote');
        expect(component.mode()).toBe('ADAPT');
        expect(component.running()).toBe(true);
        expect(component.ownedByCaller()).toBe(false);
    });

    it('merges a late status response without clobbering newer live snapshots or terminal state', () => {
        const pendingStatus = new Subject<HttpResponse<HyperionGenerationStatus>>();
        service.getStatus = () => pendingStatus.asObservable();
        const fixture = createWith(null);
        const component = fixture.componentInstance;

        component.attachToJob('live', 'GENERATE');
        service.stream$.next(snapshot('solution/A.java', 'edit', 'newer'));
        service.stream$.next({ type: 'DONE', verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 1, reasons: [] }, liveExerciseChanged: true });
        expect(component.detailsExpanded()).toBe(true);
        pendingStatus.next(
            new HttpResponse<HyperionGenerationStatus>({
                body: {
                    jobId: 'live',
                    running: true,
                    events: [{ type: 'DONE', message: 'older retained terminal', liveExerciseChanged: true, timestamp: '' }],
                    fileSnapshots: [snapshot('solution/A.java', 'create', 'older')],
                    revertAvailable: false,
                    ownedByCaller: true,
                    cancellable: true,
                },
            }),
        );

        expect(component.running()).toBe(false);
        expect(component.events().map((event) => event.type)).toContain('DONE');
        expect(component.snapshots()).toHaveLength(1);
        expect(component.snapshots()[0].content).toBe('newer');
        expect(component.detailsExpanded()).toBe(true);
    });

    it('resets and self-hides when the exercise is cleared', () => {
        const fixture = createWith({
            jobId: 'j1',
            running: false,
            events: [{ type: 'DONE', verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] } }],
            fileSnapshots: [snapshot('solution/A.java', 'create', 'a')],
        });
        const component = fixture.componentInstance;
        expect(component.visible()).toBe(true);

        fixture.componentRef.setInput('exerciseId', undefined);
        fixture.detectChanges();
        expect(component.visible()).toBe(false);
        expect(component.jobId()).toBeUndefined();
        expect(component.snapshots()).toHaveLength(0);
        expect(component.verdict()).toBeUndefined();
    });

    it('ignores a status response that arrives after the exercise is cleared', () => {
        const pendingStatus = new Subject<HttpResponse<HyperionGenerationStatus>>();
        service.getStatus = () => pendingStatus.asObservable();
        const fixture = createWith(null);
        const component = fixture.componentInstance;

        fixture.componentRef.setInput('exerciseId', undefined);
        fixture.detectChanges();
        pendingStatus.next(new HttpResponse({ body: normalizeStatus({ jobId: 'stale', running: true, events: [], fileSnapshots: [] }) }));

        expect(component.jobId()).toBeUndefined();
        expect(component.running()).toBe(false);
    });
});
