import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EMPTY, Observable, Subject, map, of, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionGenerationActivityComponent } from 'app/hyperion/exercise-generation/hyperion-generation-activity.component';
import { HyperionRunProgressComponent } from 'app/hyperion/exercise-generation/run/hyperion-run-progress.component';
import { HyperionStage } from 'app/hyperion/exercise-generation/model/hyperion-generation-stages';
import { AlertService } from 'app/foundation/service/alert.service';
import {
    ExerciseGenerationFileChange,
    ExerciseGenerationRevertResult,
    HyperionExerciseGenerationState,
    HyperionGenerationEvent,
    HyperionGenerationMessage,
    HyperionGenerationStatus,
} from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

type TestGenerationEvent = Omit<HyperionGenerationEvent, 'timestamp'> & { timestamp?: string };
type TestGenerationMessage = TestGenerationEvent | ExerciseGenerationFileChange;
type TestGenerationStatus = Omit<HyperionGenerationStatus, 'events' | 'revertAvailable' | 'ownedByCaller' | 'cancellable' | 'accountingState' | 'artifactsRetained'> & {
    events: TestGenerationEvent[];
    revertAvailable?: boolean;
    ownedByCaller?: boolean;
    cancellable?: boolean;
    accountingState?: HyperionGenerationStatus['accountingState'];
    artifactsRetained?: boolean;
};

function normalizeEvent(event: TestGenerationEvent): HyperionGenerationEvent {
    return { ...event, timestamp: event.timestamp ?? '' };
}

function normalizeMessage(message: TestGenerationMessage): HyperionGenerationMessage {
    return message.type === 'FILE_CHANGE' ? message : normalizeEvent(message);
}

function normalizeStatus(status: TestGenerationStatus): HyperionGenerationStatus {
    return {
        ...status,
        events: status.events.map(normalizeEvent),
        revertAvailable: status.revertAvailable ?? false,
        ownedByCaller: status.ownedByCaller ?? true,
        cancellable: status.cancellable ?? status.running,
        accountingState: status.accountingState ?? (status.running ? 'PENDING' : 'COMPLETE'),
        // Retention is something the server has to assert; a test that does not say so has kept nothing.
        artifactsRetained: status.artifactsRetained ?? false,
    };
}

class MockService {
    status: TestGenerationStatus | null = null;
    stream$ = new Subject<TestGenerationMessage>();
    exerciseState$ = new Subject<HyperionExerciseGenerationState>();
    cancelCalls: [number, string][] = [];

    getStatus() {
        return of(this.status ? normalizeStatus(this.status) : null);
    }

    cancel(exerciseId: number, jobId: string): Observable<void> {
        this.cancelCalls.push([exerciseId, jobId]);
        return of(undefined);
    }

    revertCalls: number[] = [];

    revertExerciseGeneration(exerciseId: number): Observable<ExerciseGenerationRevertResult> {
        this.revertCalls.push(exerciseId);
        return of({ fullyReverted: true, revertedRepositories: ['template', 'solution', 'tests'], completedAt: '2026-07-10T20:00:00Z' });
    }

    subscribeToStream(): Observable<HyperionGenerationMessage> {
        return this.stream$.pipe(map(normalizeMessage));
    }

    subscribeToExerciseState(): Observable<HyperionExerciseGenerationState> {
        return this.exerciseState$;
    }
}

function fileChange(path: string, action: 'write' | 'edit' | 'delete', overrides: Partial<ExerciseGenerationFileChange> = {}): ExerciseGenerationFileChange {
    const repo = path.startsWith('solution/') ? 'solution' : path.startsWith('template/') ? 'template' : path.startsWith('tests/') ? 'tests' : 'other';
    return { type: 'FILE_CHANGE', path, repo, action, turn: 1, timestamp: '', ...overrides };
}

/** The undo confirmation is a `tum-ui-dialog`, so its panel is portaled into the CDK overlay container, not into the fixture. */
function revertConfirmDialog(): HTMLElement | null {
    return document.querySelector('.tum-ui-dialog');
}

function revertConfirmButton(action: 'accept' | 'reject'): HTMLButtonElement {
    return document.querySelector(`[data-testid="hyperion-generation-revert-${action}"] button`)!;
}

describe('HyperionGenerationActivityComponent', () => {
    let service: MockService;
    let announcements: string[];

    beforeEach(() => {
        vi.useRealTimers();
        service = new MockService();
        announcements = [];
        TestBed.configureTestingModule({
            imports: [HyperionGenerationActivityComponent],
            providers: [
                { provide: HyperionExerciseGenerationService, useValue: service },
                { provide: TranslateService, useClass: MockTranslateService },
                // The panel links to the run page, so `routerLink` needs a router to resolve the URL against.
                provideRouter([]),
                // The liveness clock in the ladder reads the server-adjusted time, which is an HTTP-backed service.
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });
        // The status is announced through the CDK announcer, whose region lives outside the fixture.
        vi.spyOn(TestBed.inject(LiveAnnouncer), 'announce').mockImplementation((message) => {
            announcements.push(String(message));
            return Promise.resolve();
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

    it('keeps the header out of the scroll region so its actions never scroll away', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });

        const panel = fixture.nativeElement.querySelector('[data-testid="hyperion-generation-activity"]') as HTMLElement;
        const header = fixture.nativeElement.querySelector('[data-testid="hyperion-generation-activity-header"]') as HTMLElement;
        const scrollRegion = fixture.nativeElement.querySelector('[data-testid="hyperion-generation-activity-scroll"]') as HTMLElement;

        // The outer section must not scroll: that is what used to take Cancel / Run again / Undo out of view.
        expect(panel.className).not.toContain('overflow-');
        expect(scrollRegion.className).toContain('overflow-y-auto');
        expect(scrollRegion.contains(header)).toBe(false);
    });

    it('reports progress through the shared run ladder rather than a log', () => {
        const fixture = createWith({
            jobId: 'j1',
            running: true,
            events: [{ type: 'PROGRESS', phase: 'DESIGNING', message: 'Designing the exercise' }],
            fileChanges: [],
        });

        const ladder = fixture.debugElement.query(By.directive(HyperionRunProgressComponent));
        expect(ladder).not.toBeNull();
        expect(ladder.componentInstance.density()).toBe('compact');
        expect(ladder.componentInstance.stages().find((stage: HyperionStage) => stage.key === 'design')?.state).toBe('current');
        expect(ladder.componentInstance.liveMessage()).toBe('Designing the exercise');
    });

    it('reports the run state as a dot with its own word, and stops spinning once the run is terminal', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });

        const statusDot = () => fixture.nativeElement.querySelector('[data-testid="hyperion-generation-status-dot"]') as HTMLElement;
        expect(statusDot().getAttribute('data-state')).toBe('running');
        expect(statusDot().textContent).toContain('generation.status.running');
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-live-status"] fa-icon')).not.toBeNull();

        service.stream$.next({ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: true });
        fixture.detectChanges();

        expect(statusDot().getAttribute('data-state')).toBe('success');
        expect(statusDot().textContent).toContain('generation.status.saved');
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-live-status"] fa-icon')).toBeNull();
    });

    it("links to this exercise's run page once the course is known", () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-open-run"]')).toBeNull();

        fixture.componentRef.setInput('courseId', 7);
        fixture.detectChanges();

        const openRun = fixture.nativeElement.querySelector('[data-testid="hyperion-generation-open-run"]') as HTMLAnchorElement;
        expect(openRun.getAttribute('href')).toBe('/course-management/7/programming-exercises/42/generation');
    });

    it('shows an intentional idle state that can request generation', () => {
        const fixture = createWith(null);
        const startRequested = vi.fn();
        fixture.componentInstance.startRequested.subscribe(startRequested);

        expect(fixture.componentInstance.statusLoading()).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-empty"]')).not.toBeNull();

        fixture.debugElement.query(By.css('[data-testid="hyperion-generation-start"]')).triggerEventHandler('clicked');
        expect(startRequested).toHaveBeenCalledOnce();
    });

    it('locks immediately when another instructor starts generation on the public exercise topic', () => {
        const fixture = createWith(null);
        service.status = { jobId: 'remote-job', running: true, ownedByCaller: false, events: [], fileChanges: [] };

        service.exerciseState$.next({ exerciseId: 42, jobId: 'remote-job', running: true });
        fixture.detectChanges();

        expect(fixture.componentInstance.jobId()).toBe('remote-job');
        expect(fixture.componentInstance.running()).toBe(true);
        expect(fixture.componentInstance.ownedByCaller()).toBe(false);
        expect(fixture.nativeElement.textContent).toContain('generationActivity.runningByAnotherInstructor');
    });

    it('does not let an older idle status response undo the public exercise lock', () => {
        const staleIdleStatus = new Subject<HyperionGenerationStatus | null>();
        const runningStatus = normalizeStatus({ jobId: 'remote-job', running: true, ownedByCaller: false, events: [], fileChanges: [] });
        service.getStatus = vi.fn().mockReturnValueOnce(staleIdleStatus).mockReturnValue(of(runningStatus));
        const fixture = createWith(null);

        service.exerciseState$.next({ exerciseId: 42, jobId: 'remote-job', running: true });
        staleIdleStatus.next(null);
        fixture.detectChanges();

        expect(service.getStatus).toHaveBeenCalledTimes(2);
        expect(fixture.componentInstance.jobId()).toBe('remote-job');
        expect(fixture.componentInstance.running()).toBe(true);
        expect(fixture.componentInstance.ownedByCaller()).toBe(false);
    });

    it('reconciles immediately when the shared exercise topic releases the generation slot', () => {
        const fixture = createWith({ jobId: 'remote-job', running: true, ownedByCaller: false, events: [], fileChanges: [] });
        service.status = {
            jobId: 'remote-job',
            running: false,
            ownedByCaller: false,
            events: [{ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: true }],
            fileChanges: [],
        };

        service.exerciseState$.next({ exerciseId: 42, jobId: 'remote-job', running: false });
        fixture.detectChanges();

        expect(fixture.componentInstance.running()).toBe(false);
        expect(fixture.componentInstance.events().map((event) => event.type)).toContain('DONE');
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
        const pendingStatus = new Subject<HyperionGenerationStatus | null>();
        service.getStatus = vi.fn(() => pendingStatus);

        const fixture = createWith(null);

        expect(fixture.nativeElement.textContent).toContain('generationActivity.checkingStatus');
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
        vi.advanceTimersByTime(60_000);
        expect(service.getStatus).toHaveBeenCalledTimes(3);

        fixture.debugElement.query(By.css('[data-testid="hyperion-generation-status-retry"]')).triggerEventHandler('clicked');
        expect(service.getStatus).toHaveBeenCalledTimes(4);
        expect(fixture.componentInstance.statusLoading()).toBe(true);
    });

    it('keeps known authoring state available after one transient background status failure', () => {
        vi.useFakeTimers();
        const fixture = createWith(null);
        service.getStatus = vi
            .fn()
            .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })))
            .mockReturnValueOnce(of(null));

        vi.advanceTimersByTime(15_000);

        expect(service.getStatus).toHaveBeenCalledOnce();
        expect(fixture.componentInstance.statusLoadFailed()).toBe(false);
        expect(fixture.componentInstance.idle()).toBe(true);

        vi.advanceTimersByTime(15_000);

        expect(service.getStatus).toHaveBeenCalledTimes(2);
        expect(fixture.componentInstance.statusLoadFailed()).toBe(false);
        expect(fixture.componentInstance.idle()).toBe(true);
    });

    it('fails closed after repeated background status failures make the last known idle state stale', () => {
        vi.useFakeTimers();
        const fixture = createWith(null);
        service.getStatus = vi.fn(() => throwError(() => new HttpErrorResponse({ status: 503 })));

        vi.advanceTimersByTime(45_000);

        expect(service.getStatus).toHaveBeenCalledTimes(3);
        expect(fixture.componentInstance.statusLoadFailed()).toBe(true);
    });

    it('retries an authoritative status request that fails after live progress arrives', () => {
        vi.useFakeTimers();
        createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });
        const pendingStatus = new Subject<HyperionGenerationStatus | null>();
        service.getStatus = vi.fn().mockReturnValueOnce(pendingStatus).mockReturnValue(EMPTY);

        vi.advanceTimersByTime(5_000);
        expect(service.getStatus).toHaveBeenCalledOnce();

        service.stream$.next({ type: 'PROGRESS', message: 'Still running' });
        pendingStatus.error(new Error('late status failure'));
        vi.advanceTimersByTime(5_000);

        expect(service.getStatus).toHaveBeenCalledTimes(2);
    });

    it('does not let a terminal stream event cancel an in-flight authoritative status request', () => {
        vi.useFakeTimers();
        createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });
        const pendingStatus = new Subject<HyperionGenerationStatus | null>();
        service.getStatus = vi.fn().mockReturnValueOnce(pendingStatus).mockReturnValue(EMPTY);

        vi.advanceTimersByTime(5_000);
        expect(pendingStatus.observed).toBe(true);

        service.stream$.next({ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: true });

        expect(pendingStatus.observed).toBe(true);
    });

    it('does not let live progress cancel a scheduled authoritative status retry', () => {
        vi.useFakeTimers();
        const fixture = createWith(null);
        service.getStatus = vi.fn(() => throwError(() => new Error('authoritative status unavailable')));

        fixture.componentInstance.attachToJob('j1', 'ADAPT');
        expect(service.getStatus).toHaveBeenCalledOnce();

        service.stream$.next({ type: 'PROGRESS', message: 'Still running' });
        vi.advanceTimersByTime(1_000);

        expect(service.getStatus).toHaveBeenCalledTimes(2);
    });

    it('keeps polling after progress without globally locking known ownership', () => {
        vi.useFakeTimers();
        const runningStatus = normalizeStatus({ jobId: 'j1', running: true, events: [], fileChanges: [] });
        const fixture = createWith(runningStatus);
        service.getStatus = vi
            .fn()
            .mockReturnValueOnce(throwError(() => new Error('background poll failed')))
            .mockReturnValueOnce(of(runningStatus));

        vi.advanceTimersByTime(5_000);
        expect(fixture.componentInstance.statusLoadFailed()).toBe(false);

        service.stream$.next({ type: 'PROGRESS', message: 'Still running' });
        expect(fixture.componentInstance.statusLoadFailed()).toBe(false);

        vi.advanceTimersByTime(5_000);
        expect(service.getStatus).toHaveBeenCalledTimes(2);
        expect(fixture.componentInstance.statusLoadFailed()).toBe(false);
    });

    it('does not globally lock a caller-owned run after status retries are exhausted', () => {
        vi.useFakeTimers();
        const fixture = createWith(null);
        service.getStatus = vi.fn(() => throwError(() => new Error('authoritative status unavailable')));

        fixture.componentInstance.attachToJob('j1', 'ADAPT');
        expect(fixture.componentInstance.statusLoadFailed()).toBe(false);

        vi.advanceTimersByTime(1_000);
        expect(fixture.componentInstance.statusLoadFailed()).toBe(false);

        vi.advanceTimersByTime(2_000);
        expect(fixture.componentInstance.statusLoadFailed()).toBe(false);

        service.stream$.next({ type: 'PROGRESS', message: 'Still running' });
        expect(fixture.componentInstance.statusLoadFailed()).toBe(false);
    });

    it('releases the manual editor lock when retained-status requests never respond', () => {
        vi.useFakeTimers();
        service.getStatus = vi.fn(() => new Subject<HyperionGenerationStatus | null>());
        const fixture = createWith(null);

        vi.advanceTimersByTime(18_000);

        expect(service.getStatus).toHaveBeenCalledTimes(3);
        expect(fixture.componentInstance.statusLoading()).toBe(false);
        expect(fixture.componentInstance.statusLoadFailed()).toBe(true);
    });

    it('does not globally lock a caller-owned terminal event when status remains unavailable', () => {
        vi.useFakeTimers();
        const fixture = createWith(null);
        service.getStatus = vi.fn(() => throwError(() => new Error('temporary failure')));
        fixture.componentInstance.attachToJob('j1', 'ADAPT');
        vi.advanceTimersByTime(3_000);
        expect(fixture.componentInstance.statusLoadFailed()).toBe(false);

        service.stream$.next({
            type: 'DONE',
            verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 1, reasons: [] },
            liveExerciseChanged: true,
        });

        expect(fixture.componentInstance.statusLoadFailed()).toBe(false);
        expect(fixture.componentInstance.statusLoading()).toBe(false);
    });

    it('keeps caller-owned terminal state usable when its authoritative refresh fails', () => {
        const fixture = createWith(null);
        const pendingStatus = new Subject<HyperionGenerationStatus | null>();
        service.getStatus = vi.fn(() => pendingStatus);
        fixture.componentInstance.attachToJob('j1', 'ADAPT');

        service.stream$.next({
            type: 'DONE',
            verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 1, reasons: [] },
            liveExerciseChanged: true,
        });
        pendingStatus.error(new Error('late failure'));

        expect(fixture.componentInstance.statusLoadFailed()).toBe(false);
        expect(fixture.componentInstance.running()).toBe(false);
    });

    it('clears any status failure when a matched terminal refresh succeeds', () => {
        const fixture = createWith({ jobId: 'j1', mode: 'ADAPT', running: true, events: [], fileChanges: [] });
        const terminalStatus = new Subject<HyperionGenerationStatus | null>();
        const revertStatus = new Subject<HyperionGenerationStatus | null>();
        service.getStatus = vi.fn().mockReturnValueOnce(terminalStatus).mockReturnValueOnce(revertStatus);

        service.stream$.next({ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: true });
        terminalStatus.error(new Error('terminal reconciliation failed'));
        fixture.componentInstance.statusLoadFailed.set(true);

        revertStatus.next(
            normalizeStatus({
                jobId: 'j1',
                mode: 'ADAPT',
                running: false,
                events: [{ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: true }],
                fileChanges: [],
                revertAvailable: true,
            }),
        );

        expect(fixture.componentInstance.statusLoadFailed()).toBe(false);
    });

    it('does not update status after the component is destroyed', () => {
        vi.useFakeTimers();
        const pendingStatus = new Subject<HyperionGenerationStatus | null>();
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
            fileChanges: [fileChange('solution/A.java', 'write'), fileChange('tests/T.java', 'write')],
        });
        const component = fixture.componentInstance;
        expect(component.jobId()).toBe('j1');
        expect(component.fileChanges()).toHaveLength(2);
        expect(component.filesByRepo().map((group) => group.repo)).toEqual(['solution', 'tests']);
        // No disclosure to open: the files are simply there, under the ladder.
        expect(fixture.nativeElement.querySelectorAll('[data-testid="hyperion-generation-file-static"]')).toHaveLength(2);
    });

    it('has no separate details disclosure to open, because the activity now lives in the ladder', () => {
        const fixture = createWith({
            jobId: 'j1',
            running: true,
            events: [{ type: 'PROGRESS', phase: 'DESIGNING', message: 'Writing tests', timestamp: '2026-07-13T09:00:00Z' }],
            fileChanges: [fileChange('solution/A.java', 'write')],
        });

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-details-toggle"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-details"]')).toBeNull();
        expect(fixture.nativeElement.textContent).not.toContain('showDetails');
        expect(fixture.nativeElement.textContent).not.toContain('hideDetails');
        // The message is reported inside the ladder instead, which is the only place it is now shown.
        const progress = fixture.nativeElement.querySelector('[data-testid="hyperion-run-progress"]');
        expect(progress.textContent).toContain('Writing tests');
    });

    it('shows the newest message inside the ladder while announcing only the coarse running state', () => {
        const fixture = createWith({
            jobId: 'j1',
            running: true,
            events: Array.from({ length: 10 }, (_, index) => ({ type: 'PROGRESS' as const, message: `event ${index}`, timestamp: `2026-07-13T09:00:0${index}Z` })),
            fileChanges: [],
        });
        const current = fixture.nativeElement.querySelector('[data-testid="hyperion-generation-live-status"]');

        expect(current.textContent).toContain('generationActivity.running');
        expect(current.textContent).not.toContain('event 9');
        const ladder = fixture.nativeElement.querySelector('[data-testid="hyperion-run-progress"]');
        expect(ladder.querySelector('[role="status"]').textContent).toContain('event 9');
        // The compact panel keeps the clock and the meter but not the message log, which does not fit its 200px.
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-run-activity-recent"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-run-activity-liveness"]')).not.toBeNull();
        // The ladder owns exactly one live region for the stage line; the run's status dot is the only other one.
        expect(ladder.querySelectorAll('[role="status"]')).toHaveLength(1);
        expect(fixture.nativeElement.querySelector('[role="log"]')).toBeNull();
        expect(announcements).toEqual(['artemisApp.hyperion.generationActivity.running']);
    });

    it('announces the first status even though its region only appears once there is something to say', () => {
        createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });

        expect(announcements).toEqual(['artemisApp.hyperion.generationActivity.running']);
    });

    it('re-announces only when the status itself changes, not on every poll', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });

        service.stream$.next({ type: 'PROGRESS', message: 'Still editing' });
        fixture.detectChanges();
        expect(announcements).toEqual(['artemisApp.hyperion.generationActivity.running']);

        service.stream$.next({ type: 'CANCELLED' });
        fixture.detectChanges();
        expect(announcements).toHaveLength(2);
        expect(announcements[1]).toContain('artemisApp.hyperion.generationActivity.terminalStatus.CANCELLED');
    });

    it('announces a terminal outcome and where it left the exercise, instead of stale progress', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [{ type: 'PROGRESS', message: 'Still editing' }], fileChanges: [] });

        service.stream$.next({ type: 'ERROR', message: 'Generation failed' });
        fixture.detectChanges();

        const announcement = announcements.at(-1)!;
        expect(announcement).toContain('generationActivity.terminalStatus.ERROR');
        expect(announcement).toContain('generationActivity.persistence.failed');
        expect(announcement).not.toContain('Still editing');
        const terminalMessage = fixture.nativeElement.querySelector('[data-testid="hyperion-generation-terminal-message"]');
        expect(terminalMessage.textContent).toContain('Generation failed');
        expect(terminalMessage.tagName).toBe('DIV');
        expect(terminalMessage.getAttribute('role')).toBeNull();
        expect(terminalMessage.getAttribute('aria-live')).toBeNull();
        expect(terminalMessage.closest('tum-ui-message')).toBeNull();
    });

    it('announces the editor refresh before reporting the terminal result as ready', () => {
        const fixture = createWith({ jobId: 'j1', running: false, events: [{ type: 'DONE', liveExerciseChanged: true }], fileChanges: [] });

        fixture.componentRef.setInput('refreshingEditor', true);
        fixture.detectChanges();

        expect(announcements.at(-1)).toBe('artemisApp.hyperion.generationActivity.refreshingEditor');

        fixture.componentRef.setInput('refreshingEditor', false);
        fixture.detectChanges();
        expect(announcements.at(-1)).toContain('generationActivity.persistence.saved');
    });

    it('does not allow undo while the editor is applying the completed generation', () => {
        const fixture = createWith({ jobId: 'j1', running: false, revertAvailable: true, events: [{ type: 'DONE', liveExerciseChanged: true }], fileChanges: [] });

        expect(fixture.componentInstance.canRevert()).toBe(true);
        fixture.componentRef.setInput('refreshingEditor', true);
        fixture.detectChanges();

        expect(fixture.componentInstance.canRevert()).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-revert"]')).toBeNull();
    });

    it('announces when the saved exercise could not be loaded into the editor', () => {
        const fixture = createWith({ jobId: 'j1', running: false, events: [{ type: 'DONE', liveExerciseChanged: true }], fileChanges: [] });
        const refreshRequested = vi.fn();
        fixture.componentInstance.editorRefreshRequested.subscribe(refreshRequested);

        fixture.componentRef.setInput('editorRefreshFailed', true);
        fixture.detectChanges();

        const announcement = announcements.at(-1)!;
        expect(announcement).toContain('generationActivity.editorRefreshFailed');
        expect(announcement).toContain('generationActivity.persistence.saved');

        const recoveryButton = fixture.nativeElement.querySelector('[data-testid="hyperion-editor-refresh-retry"]');
        expect(recoveryButton.textContent).toContain('generationActivity.reloadSavedExercise');
        recoveryButton.querySelector('button').click();
        expect(refreshRequested).toHaveBeenCalledOnce();
    });

    it('keeps refresh recovery visible after retained activity has been cleared', () => {
        const fixture = createWith(null);
        fixture.componentRef.setInput('editorRefreshFailed', true);
        fixture.detectChanges();

        expect(fixture.componentInstance.idle()).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-editor-refresh-retry"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-empty"]')).toBeNull();
    });

    it('gives same-path files unique repository-qualified accessible names', () => {
        const fixture = createWith({
            jobId: 'j1',
            running: false,
            events: [{ type: 'DONE', liveExerciseChanged: true, savedRepositoryCommits: { template: 'template-commit' } }],
            fileChanges: [fileChange('src/Main.java', 'edit', { repo: 'solution' }), fileChange('src/Main.java', 'edit', { repo: 'template' })],
        });
        fixture.detectChanges();

        const buttons = [...fixture.nativeElement.querySelectorAll('[data-testid="hyperion-generation-file"] button')] as HTMLButtonElement[];
        expect(buttons.map((button) => button.getAttribute('aria-label'))).toEqual([
            'artemisApp.hyperion.generationActivity.repo.solution: src/Main.java',
            'artemisApp.hyperion.generationActivity.repo.template: src/Main.java',
        ]);
    });

    it('does not report retained terminal history as a newly completed generation when the live exercise did not change', () => {
        service.status = { jobId: 'j1', running: false, events: [{ type: 'DONE', liveExerciseChanged: false }], fileChanges: [] };
        const fixture = TestBed.createComponent(HyperionGenerationActivityComponent);
        const completed = vi.fn();
        fixture.componentInstance.generationCompleted.subscribe(completed);

        fixture.componentRef.setInput('exerciseId', 42);
        fixture.detectChanges();

        expect(completed).not.toHaveBeenCalled();
    });

    it('reports a retained terminal event that changed the live exercise so a newly-opened editor refreshes exactly once', () => {
        // Simulates a page opened while generation is finalizing: the component never actively observed the job (it was never `running`
        // here), but the retained status already carries a DONE event with liveExerciseChanged=true - the editor must still refresh,
        // otherwise it would keep showing the exercise as it was before the save.
        service.status = {
            jobId: 'j1',
            running: false,
            events: [{ type: 'DONE', liveExerciseChanged: true, savedRepositoryCommits: { template: 'template-commit' } }],
            fileChanges: [],
        };
        const fixture = TestBed.createComponent(HyperionGenerationActivityComponent);
        const completed = vi.fn();
        fixture.componentInstance.generationCompleted.subscribe(completed);

        fixture.componentRef.setInput('exerciseId', 42);
        fixture.detectChanges();

        expect(completed).toHaveBeenCalledOnce();
        expect(completed.mock.calls[0][0]).toMatchObject({ jobId: 'j1', liveExerciseChanged: true, savedRepositoryCommits: { template: 'template-commit' } });

        fixture.componentInstance.retryStatus();
        expect(completed).toHaveBeenCalledOnce();
    });

    it('does not manufacture a file preview before the first file arrives', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-file"]')).toBeNull();
    });

    it('coalesces live fileChanges by repository and path', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [fileChange('solution/A.java', 'write')] });
        const component = fixture.componentInstance;

        service.stream$.next(fileChange('solution/A.java', 'edit', { turn: 2 }));
        expect(component.fileChanges()).toHaveLength(1);
        expect(component.fileChanges()[0].turn).toBe(2);

        service.stream$.next(fileChange('solution/A.java', 'edit', { turn: 1 }));
        expect(component.fileChanges()[0].turn).toBe(2);

        service.stream$.next(fileChange('template/B.java', 'write'));
        expect(component.fileChanges()).toHaveLength(2);
    });

    it('sorts files by display path within each repository', () => {
        const fixture = createWith({
            jobId: 'j1',
            running: true,
            events: [],
            fileChanges: [fileChange('solution/Z.java', 'write'), fileChange('solution/A.java', 'write')],
        });

        expect(fixture.componentInstance.filesByRepo()[0].files.map((entry) => entry.file.path)).toEqual(['solution/A.java', 'solution/Z.java']);
    });

    it('keeps live changed-file controls non-actionable until the run is terminal', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });
        const component = fixture.componentInstance;
        const selected = vi.fn();
        component.fileChangeSelected.subscribe(selected);

        service.stream$.next(fileChange('src/Main.java', 'write', { repo: 'template' }));
        service.stream$.next(fileChange('src/Main.java', 'write', { repo: 'solution' }));
        fixture.detectChanges();

        expect(component.fileChanges()).toHaveLength(2);
        expect(component.filesByRepo().map((group) => [group.repo, group.files.map((entry) => entry.file.path)])).toEqual([
            ['solution', ['src/Main.java']],
            ['template', ['src/Main.java']],
        ]);

        expect(fixture.nativeElement.querySelectorAll('[data-testid="hyperion-generation-file"]')).toHaveLength(0);
        expect(fixture.nativeElement.querySelectorAll('[data-testid="hyperion-generation-file-static"]')).toHaveLength(2);
        expect(selected).not.toHaveBeenCalled();
    });

    it('emits a selected fileChange after the run reaches a persisted terminal state', () => {
        const fixture = createWith({
            jobId: 'j1',
            running: false,
            events: [{ type: 'DONE', message: 'Saved', completionStatus: 'SUCCESS', liveExerciseChanged: true }],
            fileChanges: [fileChange('solution/src/Main.java', 'edit')],
        });
        const selected = vi.fn();
        fixture.componentInstance.fileChangeSelected.subscribe(selected);

        fixture.detectChanges();
        const changedFile = fixture.nativeElement.querySelector('[data-testid="hyperion-generation-file"] button') as HTMLButtonElement;
        changedFile.click();

        expect(changedFile.disabled).toBe(false);
        expect(selected).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ repo: 'solution', path: 'solution/src/Main.java' }));
    });

    it('shows deleted files without offering navigation', () => {
        const fixture = createWith({
            jobId: 'j1',
            running: false,
            events: [{ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: true }],
            fileChanges: [fileChange('solution/src/Removed.java', 'delete')],
        });
        const selected = vi.fn();
        fixture.componentInstance.fileChangeSelected.subscribe(selected);

        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-file"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-file-static"]')?.textContent).toContain('src/Removed.java');
        expect(selected).not.toHaveBeenCalled();
    });

    it('offers repository diff actions only when persistence returned an exact commit', () => {
        const fixture = createWith({
            jobId: 'job-42',
            running: false,
            events: [{ type: 'DONE', liveExerciseChanged: true, savedExerciseVersionId: 17, savedRepositoryCommits: { solution: 'solution-commit', tests: 'tests-commit' } }],
            fileChanges: [
                fileChange('problem-statement.md', 'edit'),
                fileChange('solution/src/Main.java', 'edit'),
                fileChange('solution/src/Helper.java', 'edit'),
                fileChange('tests/src/MainTest.java', 'edit'),
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
            events: [{ type: 'DONE', liveExerciseChanged: true, savedExerciseVersionId: 17, savedRepositoryCommits: { template: 'template-commit' } }],
            fileChanges: [fileChange('template/src/Main.java', 'edit')],
        });
        const requested = vi.fn();
        fixture.componentInstance.reviewRequested.subscribe(requested);

        fixture.debugElement.query(By.css('[data-review-target="template"]')).triggerEventHandler('clicked');

        expect(requested).toHaveBeenCalledExactlyOnceWith({ target: 'template', jobId: 'job-42', commitHash: 'template-commit' });
    });

    it('emits the exact saved exercise version for problem-statement review', () => {
        const fixture = createWith({
            jobId: 'job-42',
            running: false,
            events: [{ type: 'DONE', liveExerciseChanged: true, savedExerciseVersionId: 17 }],
            fileChanges: [fileChange('problem-statement.md', 'edit')],
        });
        const requested = vi.fn();
        fixture.componentInstance.reviewRequested.subscribe(requested);

        fixture.componentInstance.requestReview('problem-statement');

        expect(requested).toHaveBeenCalledExactlyOnceWith({ target: 'problem-statement', jobId: 'job-42', savedExerciseVersionId: 17 });
    });

    it('does not offer an inexact review action when retained status has no saved artifact identity', () => {
        const fixture = createWith({
            jobId: 'job-42',
            mode: 'GENERATE',
            running: false,
            revertAvailable: true,
            revertJobId: 'job-42',
            revertMode: 'GENERATE',
            events: [],
            fileChanges: [],
        });
        fixture.detectChanges();

        const actions = fixture.debugElement.queryAll(By.css('[data-testid="hyperion-generation-review-action"]'));
        expect(actions).toHaveLength(0);
    });

    it('does not offer saved-change review before persistence succeeds', () => {
        const fixture = createWith({
            jobId: 'job-42',
            running: false,
            events: [{ type: 'DONE', completionStatus: 'PARTIAL', liveExerciseChanged: false }],
            fileChanges: [fileChange('solution/src/Main.java', 'edit')],
        });

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-review"]')).toBeNull();
    });

    it('keeps fileChanges non-actionable when a terminal run did not change the live exercise', () => {
        const fixture = createWith({
            jobId: 'j1',
            running: false,
            events: [{ type: 'DONE', message: 'Draft only', completionStatus: 'PARTIAL', liveExerciseChanged: false }],
            fileChanges: [fileChange('solution/src/Main.java', 'edit')],
        });
        fixture.detectChanges();

        // Nothing was saved, so the files are reported but nothing can be opened from here.
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-file"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-file-static"]')).not.toBeNull();
    });

    it.each([
        [{ running: true, events: [] }, 'persistence.workingCopy'],
        [{ running: false, events: [{ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: true }] }, 'persistence.saved'],
        [{ running: false, events: [{ type: 'DONE', completionStatus: 'NEEDS_REVIEW', liveExerciseChanged: true }] }, 'persistence.savedNeedsReview'],
        [{ running: false, events: [{ type: 'DONE', completionStatus: 'PARTIAL', liveExerciseChanged: false }] }, 'persistence.partial'],
        [{ running: false, events: [{ type: 'DONE', completionStatus: 'NEEDS_REVIEW', liveExerciseChanged: false }] }, 'persistence.notSaved'],
        [{ running: false, events: [{ type: 'CANCELLED' }] }, 'persistence.cancelled'],
        [{ running: false, events: [{ type: 'ERROR' }] }, 'persistence.failed'],
    ])('shows the persistence state for %o', (state, labelKey) => {
        const fixture = createWith({ jobId: 'j1', fileChanges: [], ...state } as TestGenerationStatus);

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-persistence-state"]').textContent).toContain(labelKey);
    });

    it.each([
        [{ type: 'ERROR' as const }, 'GENERATE' as const],
        [{ type: 'CANCELLED' as const }, 'ADAPT' as const],
        [{ type: 'DONE' as const, completionStatus: 'PARTIAL' as const }, 'GENERATE' as const],
    ])('offers a safe retry after %o', (event, mode) => {
        const fixture = createWith({ jobId: 'j1', mode, running: false, events: [event], fileChanges: [] });
        const requested = vi.fn();
        fixture.componentInstance.startRequested.subscribe(requested);
        fixture.detectChanges();

        fixture.debugElement.query(By.css('[data-testid="hyperion-generation-run-again"]')).triggerEventHandler('clicked');
        expect(requested).toHaveBeenCalledExactlyOnceWith(mode);
    });

    it('does not offer a retry when the exercise is no longer eligible', () => {
        const fixture = createWith({ jobId: 'j1', mode: 'GENERATE', running: false, events: [{ type: 'ERROR' }], fileChanges: [] });
        fixture.componentRef.setInput('startAllowed', false);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-run-again"]')).toBeNull();
        expect(fixture.componentInstance.canRunAgain()).toBe(false);
    });

    it('keeps partial-save instructions visible when Run again is offered', () => {
        const fixture = createWith({
            jobId: 'j1',
            mode: 'GENERATE',
            running: false,
            events: [{ type: 'DONE', completionStatus: 'PARTIAL', message: 'Review the partially saved exercise manually.' }],
            fileChanges: [],
        });
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-terminal-message"]').textContent).toContain('partially saved exercise');
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-run-again"]')).not.toBeNull();
    });

    it('reconciles cancellation status a bounded number of times when the terminal stream event stalls', () => {
        vi.useFakeTimers();
        service.status = { jobId: 'j1', mode: 'GENERATE', running: true, events: [], fileChanges: [] };
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
        const fixture = createWith({ jobId: 'j1', mode: 'GENERATE', running: true, events: [], fileChanges: [] });
        service.status = null;

        fixture.componentInstance.cancel();
        vi.advanceTimersByTime(1_000);

        expect(fixture.componentInstance.cancelRequested()).toBe(false);
        expect(fixture.componentInstance.running()).toBe(false);
        expect(fixture.componentInstance.jobId()).toBeUndefined();
    });

    it('adopts a newer job when cancellation reconciliation returns a different job', () => {
        vi.useFakeTimers();
        const fixture = createWith({ jobId: 'j1', mode: 'GENERATE', running: true, events: [], fileChanges: [] });
        service.status = { jobId: 'j2', mode: 'ADAPT', running: true, events: [], fileChanges: [] };

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
            fileChanges: [],
        });
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-revert"]').textContent).toContain('generationActivity.undoGeneration');
    });

    it('caps retained progress events to the latest entries', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });
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
            fileChanges: [],
        });
        const component = fixture.componentInstance;

        expect(component.events()).toHaveLength(50);
        expect(component.events()[0]?.message).toBe('event 5');
        expect(component.events()[49]?.message).toBe('event 54');
    });

    it('records the terminal verdict from the stream', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });
        const component = fixture.componentInstance;
        const completed = vi.fn();
        component.generationCompleted.subscribe(completed);

        service.stream$.next({
            type: 'DONE',
            completionStatus: 'NEEDS_REVIEW',
            verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 5, reasons: [] },
        });
        fixture.detectChanges();

        expect(component.running()).toBe(false);
        expect(component.verdict()?.mechanicallyVerified).toBe(true);
        expect(component.completionStatus()).toBe('NEEDS_REVIEW');
        expect(completed).toHaveBeenCalledExactlyOnceWith({
            jobId: 'j1',
            mode: undefined,
            verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 5, reasons: [] },
            completionStatus: 'NEEDS_REVIEW',
            liveExerciseChanged: undefined,
            completedAt: '',
        });
    });

    it('reconciles retained fileChanges when a terminal event overtakes an earlier file message', () => {
        const fixture = createWith({
            jobId: 'j1',
            mode: 'GENERATE',
            running: true,
            events: [],
            fileChanges: [fileChange('solution/A.java', 'write', { turn: 1 })],
        });
        service.status = {
            jobId: 'j1',
            mode: 'GENERATE',
            running: false,
            events: [{ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: true }],
            fileChanges: [fileChange('solution/A.java', 'edit', { turn: 2 })],
        };

        service.stream$.next({ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: true });

        expect(fixture.componentInstance.fileChanges()).toHaveLength(1);
        expect(fixture.componentInstance.fileChanges()[0].turn).toBe(2);
    });

    // The per-check verdict strip is the run page's job; this panel reports the run's outcome and its progress ladder.
    it("does not repeat the run page's per-check verdict breakdown", () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });

        service.stream$.next({
            type: 'DONE',
            completionStatus: 'SUCCESS',
            verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 1, reasons: [] },
        });
        fixture.detectChanges();

        expect(fixture.componentInstance.verdict()?.mechanicallyVerified).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-verdict"]')).toBeNull();
        expect(fixture.nativeElement.textContent).not.toContain('verdict.templateFailedExpected');
    });

    it('requests cancellation for the owner', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });
        fixture.componentInstance.cancel();
        expect(service.cancelCalls).toEqual([[42, 'j1']]);
        expect(fixture.componentInstance.cancelRequested()).toBe(true);
    });

    it('reports a cancellation request failure and refreshes status', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });
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
            fileChanges: [],
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

        component.confirmRevert();
        component.acceptRevert();
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
        const status: TestGenerationStatus = { jobId: 'j1', mode: 'ADAPT', running: true, events: [], fileChanges: [] };
        const terminalStatus$ = new Subject<HyperionGenerationStatus | null>();
        const availability$ = new Subject<HyperionGenerationStatus | null>();
        service.getStatus = vi
            .fn()
            .mockReturnValueOnce(of(normalizeStatus(status)))
            .mockReturnValueOnce(terminalStatus$)
            .mockReturnValueOnce(availability$);
        const fixture = createWith(status);

        service.stream$.next({ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: true });
        fixture.destroy();
        const timersAfterDestroy = vi.getTimerCount();
        availability$.error(new Error('late failure'));
        expect(vi.getTimerCount()).toBe(timersAfterDestroy);
        vi.advanceTimersByTime(60_000);

        expect(service.getStatus).toHaveBeenCalledTimes(3);
    });

    it('refreshes retained status when cancellation is rejected or already terminal', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });
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
            fileChanges: [],
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
        expect(component.jobId()).toBe('j9');
        expect(component.running()).toBe(true);
        expect(component.runningLabelKey()).toBe('artemisApp.hyperion.generationActivity.adapting');
    });

    it('shows a sanitized active run owned by another instructor without exposing cancellation', () => {
        const subscribeToStream = vi.spyOn(service, 'subscribeToStream');
        const fixture = createWith({ jobId: 'other-job', mode: 'GENERATE', running: true, ownedByCaller: false, events: [], fileChanges: [] });
        fixture.detectChanges();

        expect(fixture.componentInstance.running()).toBe(true);
        expect(fixture.componentInstance.ownedByCaller()).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-cancel"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-live-status"]')?.textContent).toContain('runningByAnotherInstructor');
        expect(subscribeToStream).not.toHaveBeenCalled();

        fixture.componentInstance.cancel();
        expect(service.cancelCalls).toEqual([]);
    });

    it('uses a sanitized terminal outcome instead of inferring that another instructor changed the exercise', () => {
        vi.useFakeTimers();
        const fixture = createWith({ jobId: 'other-job', mode: 'GENERATE', running: true, ownedByCaller: false, events: [], fileChanges: [] });
        const completed = vi.fn();
        fixture.componentInstance.generationCompleted.subscribe(completed);
        expect(fixture.componentInstance.running()).toBe(true);

        service.status = {
            jobId: 'other-job',
            mode: 'GENERATE',
            running: false,
            ownedByCaller: false,
            events: [{ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: false }],
            fileChanges: [],
        };
        vi.advanceTimersByTime(5_000);

        expect(completed).toHaveBeenCalledOnce();
        expect(completed.mock.calls[0][0]).toMatchObject({ mode: 'GENERATE', completionStatus: 'SUCCESS', liveExerciseChanged: false });
        expect(fixture.componentInstance.running()).toBe(false);
        expect(fixture.componentInstance.jobId()).toBe('other-job');
    });

    it('treats missing ownership fields as non-owned and non-cancellable', () => {
        const malformedStatus = {
            jobId: 'unknown-owner-job',
            mode: 'GENERATE',
            running: true,
            events: [],
            fileChanges: [],
            revertAvailable: false,
        } as unknown as HyperionGenerationStatus;
        service.getStatus = vi.fn(() => of(malformedStatus));
        const subscribeToStream = vi.spyOn(service, 'subscribeToStream');

        const fixture = createWith(null);

        expect(fixture.componentInstance.ownedByCaller()).toBe(false);
        expect(fixture.componentInstance.cancellable()).toBe(false);
        expect(subscribeToStream).not.toHaveBeenCalled();
        fixture.componentInstance.cancel();
        expect(service.cancelCalls).toEqual([]);
    });

    it('queues the authoritative status read until an asynchronous initial request completes', async () => {
        const running = normalizeStatus({ jobId: 'j1', mode: 'GENERATE', running: true, events: [], fileChanges: [] });
        const done = normalizeStatus({
            jobId: 'j1',
            mode: 'GENERATE',
            running: false,
            events: [{ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: true }],
            fileChanges: [],
        });
        const initialStatus = new Subject<HyperionGenerationStatus | null>();
        service.getStatus = vi.fn().mockReturnValueOnce(initialStatus).mockReturnValueOnce(of(done));
        const subscribeToStream = vi.spyOn(service, 'subscribeToStream');

        const fixture = createWith(null);
        initialStatus.next(running);
        initialStatus.complete();
        await Promise.resolve();

        expect(subscribeToStream).toHaveBeenCalledOnce();
        expect(service.getStatus).toHaveBeenCalledTimes(2);
        expect(fixture.componentInstance.running()).toBe(false);
        expect(fixture.componentInstance.completionStatus()).toBe('SUCCESS');
    });

    it('hides cancellation while the owner run is finalizing', () => {
        const fixture = createWith({ jobId: 'j1', mode: 'GENERATE', running: true, ownedByCaller: true, cancellable: false, events: [], fileChanges: [] });
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
            fileChanges: [fileChange('solution/A.java', 'write')],
        };

        component.attachToJob('j9', 'GENERATE');

        expect(component.events()).toEqual([{ type: 'PROGRESS', message: 'already produced files', timestamp: '' }]);
        expect(component.fileChanges()).toHaveLength(1);
        expect(component.fileChanges()[0].path).toBe('solution/A.java');
    });

    it('confirms undo for a mechanically verified adaptation and leaves one truthful terminal state', async () => {
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
            fileChanges: [],
            revertAvailable: true,
        };
        service.stream$.next(fileChange('solution/A.java', 'write'));
        expect(component.fileChanges()[0].path).toBe('solution/A.java');
        expect(component.canRevert()).toBe(false);

        service.stream$.next({
            type: 'DONE',
            completionStatus: 'SUCCESS',
            verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] },
            liveExerciseChanged: true,
        });
        expect(component.running()).toBe(false);
        expect(component.canRevert()).toBe(true);

        fixture.detectChanges();
        fixture.debugElement.query(By.css('[data-testid="hyperion-generation-revert"]')).triggerEventHandler('clicked');
        fixture.detectChanges();
        await fixture.whenStable();

        expect(service.revertCalls).toEqual([]);
        expect(revertConfirmDialog()?.textContent).toContain('undoAdaptationConfirmHeader');
        expect(revertConfirmDialog()?.textContent).toContain('undoAdaptationConfirmMessage');
        // The destructive action must not be pre-focused, which is what the replaced confirm dialog expressed as `defaultFocus: 'reject'`.
        expect(document.activeElement).not.toBe(revertConfirmButton('accept'));

        revertConfirmButton('accept').click();

        expect(service.revertCalls).toEqual([42]);
        expect(component.reverted()).toBe(true);
        expect(component.jobId()).toBeUndefined();
        expect(component.mode()).toBe('ADAPT');
        expect(component.verdict()).toBeUndefined();
        expect(component.fileChanges()).toEqual([]);
        expect(reverted).toHaveBeenCalledExactlyOnceWith('2026-07-10T20:00:00Z');
        expect(component.canRevert()).toBe(false);
    });

    it('retains diagnostic events and fileChanges with a persistent warning when undo is partial, and still refreshes the editor', () => {
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
            fileChanges: [],
            revertAvailable: true,
        };
        service.stream$.next(fileChange('solution/A.java', 'write'));
        service.stream$.next({
            type: 'DONE',
            completionStatus: 'SUCCESS',
            verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] },
            liveExerciseChanged: true,
        });

        component.confirmRevert();
        expect(component.confirmRevertVisible()).toBe(true);
        component.acceptRevert();

        expect(service.revertCalls).toEqual([42]);
        expect(component.reverted()).toBe(false);
        expect(component.fileChanges()).toHaveLength(1);
        expect(component.fileChanges()[0]).toMatchObject({ path: 'solution/A.java', action: 'write' });
        expect(component.events()).toContainEqual(expect.objectContaining({ type: 'DONE', completionStatus: 'SUCCESS' }));
        expect(component.verdict()).toBeUndefined();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-revert-partial"]')).not.toBeNull();
        // A partial revert still reset the TEMPLATE repository on the server, so the editor must be refreshed - otherwise it would keep
        // showing the pre-revert template content even though the error alert (asserted above) already told the instructor something failed.
        expect(reverted).toHaveBeenCalledExactlyOnceWith('2026-07-10T20:00:00Z');
        expect(component.canRevert()).toBe(true);
    });

    it('does not refresh the editor when a partial revert reset no repositories', () => {
        const fixture = createWith(null);
        const component = fixture.componentInstance;
        const reverted = vi.fn();
        component.generationReverted.subscribe(reverted);
        service.revertExerciseGeneration = (exerciseId: number) => {
            service.revertCalls.push(exerciseId);
            return throwError(() => new HttpErrorResponse({ status: 409, error: { fullyReverted: false, revertedRepositories: [], completedAt: '2026-07-10T20:00:00Z' } }));
        };

        component.attachToJob('j9', 'ADAPT');
        service.status = {
            jobId: 'j9',
            running: false,
            mode: 'ADAPT',
            events: [],
            fileChanges: [],
            revertAvailable: true,
        };
        service.stream$.next({
            type: 'DONE',
            completionStatus: 'SUCCESS',
            verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] },
            liveExerciseChanged: true,
        });

        component.confirmRevert();
        expect(component.confirmRevertVisible()).toBe(true);
        component.acceptRevert();

        expect(service.revertCalls).toEqual([42]);
        expect(reverted).not.toHaveBeenCalled();
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
                    verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 4, reasons: [] },
                    liveExerciseChanged: true,
                },
            ],
            fileChanges: [],
            revertAvailable: true,
        });
        const component = fixture.componentInstance;

        expect(component.mode()).toBe('ADAPT');
        expect(component.runningLabelKey()).toBe('artemisApp.hyperion.generationActivity.adapting');
        expect(component.canRevert()).toBe(true);
    });

    it('does not offer revert for a mechanically verified generate run', () => {
        const fixture = createWith(null);
        const component = fixture.componentInstance;

        component.attachToJob('j9', 'GENERATE');
        service.stream$.next({ type: 'DONE', verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] } });
        expect(component.canRevert()).toBe(false);
    });

    it('does not offer revert when a mechanically verified adaptation was only partially saved', () => {
        const fixture = createWith(null);
        const component = fixture.componentInstance;

        component.attachToJob('j9', 'ADAPT');
        service.stream$.next({
            type: 'DONE',
            completionStatus: 'PARTIAL',
            verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] },
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
                    verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] },
                    liveExerciseChanged: true,
                },
            ],
            fileChanges: [],
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
            fileChanges: [],
            revertAvailable: true,
            revertJobId: 'successful-adaptation',
            revertMode: 'ADAPT',
        });

        expect(fixture.componentInstance.canRevert()).toBe(true);
        expect(fixture.componentInstance.undoLabelKey()).toBe('artemisApp.hyperion.generationActivity.undoAdaptation');
        const review = vi.fn();
        fixture.componentInstance.reviewRequested.subscribe(review);
        fixture.componentInstance.requestReview('problem-statement');
        expect(review).not.toHaveBeenCalled();

        const success = vi.spyOn(TestBed.inject(AlertService), 'success');
        fixture.componentInstance.confirmRevert();
        fixture.detectChanges();
        expect(revertConfirmDialog()?.textContent).toContain('undoAdaptationConfirmHeader');
        expect(revertConfirmDialog()?.textContent).toContain('undoAdaptationConfirmMessage');
        fixture.componentInstance.acceptRevert();
        expect(success).toHaveBeenCalledWith('artemisApp.hyperion.generationActivity.undoAdaptationSuccess');
        expect(fixture.componentInstance.undoneLabelKey()).toBe('artemisApp.hyperion.generationActivity.adaptationUndone');
    });

    it('shows a retained undo without manufacturing empty activity details', () => {
        const fixture = createWith({
            jobId: 'j9',
            running: false,
            mode: 'ADAPT',
            events: [],
            fileChanges: [],
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
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });
        const component = fixture.componentInstance;
        service.status = { jobId: 'j1', running: true, events: [{ type: 'PROGRESS', message: 'still running' }], fileChanges: [] };
        expect(component.running()).toBe(true);

        service.stream$.error(new Error('ws dropped'));
        vi.advanceTimersByTime(1_000);
        expect(component.running()).toBe(true);
        expect(component.events()).toEqual([{ type: 'PROGRESS', message: 'still running', timestamp: '' }]);
    });

    it('periodically reconciles an active stream so a lost terminal message cannot leave the editor stuck', () => {
        vi.useFakeTimers();
        const fixture = createWith({ jobId: 'j1', mode: 'GENERATE', running: true, events: [], fileChanges: [] });
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
            fileChanges: [],
        };

        vi.advanceTimersByTime(5_000);

        expect(component.running()).toBe(false);
        expect(component.completionStatus()).toBe('SUCCESS');
        expect(completed).toHaveBeenCalledExactlyOnceWith({
            jobId: 'j1',
            mode: 'GENERATE',
            verdict: undefined,
            completionStatus: 'SUCCESS',
            liveExerciseChanged: true,
            completedAt: '',
        });
    });

    it('replaces a stale local fileChange with a newer retained status fileChange after stream loss', () => {
        vi.useFakeTimers();
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [fileChange('solution/A.java', 'write', { turn: 1 })] });
        const component = fixture.componentInstance;
        service.status = {
            jobId: 'j1',
            running: true,
            events: [{ type: 'PROGRESS', message: 'status caught up' }],
            fileChanges: [fileChange('solution/A.java', 'edit', { turn: 2 })],
        };

        service.stream$.error(new Error('ws dropped'));
        vi.advanceTimersByTime(1_000);

        expect(component.fileChanges()).toHaveLength(1);
        expect(component.fileChanges()[0].turn).toBe(2);
    });

    it('polls status instead of staying stuck when the stream completes without an event', () => {
        vi.useFakeTimers();
        service.subscribeToStream = () => EMPTY;
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });
        const component = fixture.componentInstance;
        const completed = vi.fn();
        component.generationCompleted.subscribe(completed);
        service.status = {
            jobId: 'j1',
            running: false,
            events: [{ type: 'DONE', verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] }, liveExerciseChanged: true }],
            fileChanges: [],
        };

        vi.advanceTimersByTime(1_000);

        expect(component.running()).toBe(false);
        expect(component.verdict()?.mechanicallyVerified).toBe(true);
        expect(completed).toHaveBeenCalledExactlyOnceWith({
            jobId: 'j1',
            mode: undefined,
            verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] },
            completionStatus: undefined,
            liveExerciseChanged: true,
            completedAt: '',
        });
    });

    it('stops running on a CANCELLED terminal event from the stream', () => {
        const fixture = createWith({ jobId: 'j1', running: true, mode: 'ADAPT', events: [], fileChanges: [] });
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
            fileChanges: [],
            revertAvailable: false,
            ownedByCaller: true,
            cancellable: true,
            accountingState: 'PENDING',
            artifactsRetained: false,
        };
        const stoppedStatus = { ...runningStatus, running: false, events: [{ type: 'CANCELLED' as const, message: 'Cancelled' }], accountingState: 'COMPLETE' as const };
        service.getStatus = vi
            .fn()
            .mockReturnValueOnce(of(runningStatus))
            .mockReturnValueOnce(of(stoppedStatus))
            .mockReturnValueOnce(of({ ...stoppedStatus, revertAvailable: true }));
        component.attachToJob('later-run', 'GENERATE');

        service.stream$.next({ type: 'CANCELLED', message: 'Cancelled' });

        expect(component.canRevert()).toBe(true);
        expect(service.getStatus).toHaveBeenCalledTimes(3);
    });

    it.each([
        ['CANCELLED' as const, 'terminalStatus.CANCELLED'],
        ['ERROR' as const, 'terminalStatus.ERROR'],
    ])('keeps the %s outcome visible in the header', (type, labelKey) => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });
        service.stream$.next({ type, message: 'terminal' });
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain(labelKey);
    });

    it.each([
        [
            {
                type: 'DONE' as const,
                completionStatus: 'SUCCESS' as const,
                verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] },
                liveExerciseChanged: true,
            },
            true,
        ],
        [
            {
                type: 'DONE' as const,
                completionStatus: 'PARTIAL' as const,
                verdict: { mechanicallyVerified: false, solutionPassed: false, templateFailed: true, testCount: 3, reasons: [] },
            },
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
            fileChanges: [fileChange('solution/A.java', 'write')],
            revertAvailable: canRevert,
        });
        const component = fixture.componentInstance;

        expect(component.running()).toBe(false);
        expect(component.events().at(-1)).toEqual({ ...terminalEvent, timestamp: '' });
        expect(component.canRevert()).toBe(canRevert);
        if ('completionStatus' in terminalEvent) {
            expect(component.completionStatus()).toBe(terminalEvent.completionStatus);
        }
    });

    it('does not let a late status response clobber a freshly attached live run', () => {
        const pendingStatus = new Subject<HyperionGenerationStatus | null>();
        service.getStatus = () => pendingStatus.asObservable();
        const fixture = createWith(null);
        const component = fixture.componentInstance;

        component.attachToJob('live', 'GENERATE');
        expect(component.jobId()).toBe('live');
        expect(component.running()).toBe(true);

        pendingStatus.next(normalizeStatus({ jobId: 'stale', running: false, events: [], fileChanges: [] }));
        expect(component.jobId()).toBe('live');
        expect(component.running()).toBe(true);
    });

    it('adopts a newer active run owned by the same instructor', () => {
        const pendingStatus = new Subject<HyperionGenerationStatus | null>();
        service.getStatus = () => pendingStatus.asObservable();
        const fixture = createWith(null);
        const component = fixture.componentInstance;

        component.attachToJob('previous', 'GENERATE');
        pendingStatus.next(normalizeStatus({ jobId: 'current', mode: 'ADAPT', running: true, ownedByCaller: true, events: [], fileChanges: [] }));

        expect(component.jobId()).toBe('current');
        expect(component.mode()).toBe('ADAPT');
        expect(component.running()).toBe(true);
    });

    it('discovers a run started after this editor became idle', () => {
        vi.useFakeTimers();
        const fixture = createWith(null);
        const component = fixture.componentInstance;
        service.status = { jobId: 'remote', mode: 'ADAPT', running: true, ownedByCaller: false, events: [], fileChanges: [] };

        vi.advanceTimersByTime(15_000);

        expect(component.jobId()).toBe('remote');
        expect(component.mode()).toBe('ADAPT');
        expect(component.running()).toBe(true);
        expect(component.ownedByCaller()).toBe(false);
    });

    it('merges a late status response without clobbering newer live fileChanges or terminal state', () => {
        const pendingStatus = new Subject<HyperionGenerationStatus | null>();
        service.getStatus = () => pendingStatus.asObservable();
        const fixture = createWith(null);
        const component = fixture.componentInstance;

        component.attachToJob('live', 'GENERATE');
        service.stream$.next(fileChange('solution/A.java', 'edit'));
        service.stream$.next({
            type: 'DONE',
            verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 1, reasons: [] },
            liveExerciseChanged: true,
        });
        pendingStatus.next({
            jobId: 'live',
            running: true,
            events: [{ type: 'DONE', message: 'older retained terminal', liveExerciseChanged: true, timestamp: '' }],
            fileChanges: [fileChange('solution/A.java', 'write')],
            revertAvailable: false,
            ownedByCaller: true,
            cancellable: true,
            accountingState: 'PENDING',
            artifactsRetained: false,
        });

        expect(component.running()).toBe(false);
        expect(component.events().map((event) => event.type)).toContain('DONE');
        expect(component.fileChanges()).toHaveLength(1);
        expect(component.fileChanges()[0].action).toBe('edit');
    });

    it('resets and self-hides when the exercise is cleared', () => {
        const fixture = createWith({
            jobId: 'j1',
            running: false,
            events: [{ type: 'DONE', verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] } }],
            fileChanges: [fileChange('solution/A.java', 'write')],
        });
        const component = fixture.componentInstance;
        expect(component.visible()).toBe(true);

        fixture.componentRef.setInput('exerciseId', undefined);
        fixture.detectChanges();
        expect(component.visible()).toBe(false);
        expect(component.jobId()).toBeUndefined();
        expect(component.fileChanges()).toHaveLength(0);
        expect(component.verdict()).toBeUndefined();
    });

    it('ignores a status response that arrives after the exercise is cleared', () => {
        const pendingStatus = new Subject<HyperionGenerationStatus | null>();
        service.getStatus = () => pendingStatus.asObservable();
        const fixture = createWith(null);
        const component = fixture.componentInstance;

        fixture.componentRef.setInput('exerciseId', undefined);
        fixture.detectChanges();
        pendingStatus.next(normalizeStatus({ jobId: 'stale', running: true, events: [], fileChanges: [] }));

        expect(component.jobId()).toBeUndefined();
        expect(component.running()).toBe(false);
    });
});
