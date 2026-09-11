import { Component, inject, input } from '@angular/core';
import { HyperionGenerationActivityFacade } from './hyperion-generation-activity.facade';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EMPTY, Observable, Subject, map, of, throwError } from 'rxjs';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
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

@Component({ template: '', providers: [HyperionGenerationActivityFacade] })
class RunStateHost {
    readonly exerciseId = input<number>();
    readonly refreshingEditor = input(false);
    readonly facade = inject(HyperionGenerationActivityFacade);
    constructor() {
        this.facade.connect({ exerciseId: this.exerciseId, refreshingEditor: this.refreshingEditor });
    }
}
describe('HyperionGenerationActivityFacade', () => {
    let service: MockService;
    beforeEach(() => {
        service = new MockService();
        TestBed.configureTestingModule({
            imports: [RunStateHost],
            providers: [
                { provide: HyperionExerciseGenerationService, useValue: service },
                { provide: AlertService, useValue: { error: vi.fn(), warning: vi.fn(), success: vi.fn() } },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });
    });
    afterEach(() => {
        TestBed.resetTestingModule();
        vi.useRealTimers();
    });
    function createWith(status: TestGenerationStatus | null) {
        service.status = status;
        const fixture = TestBed.createComponent(RunStateHost);
        fixture.componentRef.setInput('exerciseId', 42);
        fixture.detectChanges();
        return fixture;
    }
    it('shows input after attaching to a job even before the design exists', () => {
        const fixture = createWith(null);
        const input = { prompt: 'Keep the API.' };
        service.status = { jobId: 'j2', mode: 'ADAPT', running: true, ownedByCaller: true, events: [], fileChanges: [], input };
        fixture.componentInstance.facade.attachToJob('j2', 'ADAPT');
        fixture.detectChanges();
        expect(fixture.componentInstance.facade.specDocument()).toBeUndefined();
        expect(fixture.componentInstance.facade.input()).toEqual(input);
    });

    it('replays the owner input and clears it when switching to another exercise', () => {
        const input = { prompt: 'Keep the API.', reviewFeedback: [{ targetType: 'SOLUTION_REPO', comments: ['Handle empty stacks.'] }] };
        const fixture = createWith({ jobId: 'j1', running: true, ownedByCaller: true, events: [], fileChanges: [], input });
        expect(fixture.componentInstance.facade.input()).toEqual(input);

        service.status = { jobId: 'j2', running: true, ownedByCaller: false, events: [], fileChanges: [] };
        fixture.componentRef.setInput('exerciseId', 43);
        fixture.detectChanges();
        expect(fixture.componentInstance.facade.input()).toBeUndefined();
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
        expect(fixture.componentInstance.facade.jobId()).toBe('remote-job');
        expect(fixture.componentInstance.facade.running()).toBe(true);
        expect(fixture.componentInstance.facade.ownedByCaller()).toBe(false);
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

        expect(fixture.componentInstance.facade.running()).toBe(false);
        expect(fixture.componentInstance.facade.events().map((event) => event.type)).toContain('DONE');
    });
    it('fails closed after repeated background status failures make the last known idle state stale', () => {
        vi.useFakeTimers();
        const fixture = createWith(null);
        service.getStatus = vi.fn(() => throwError(() => new HttpErrorResponse({ status: 503 })));

        vi.advanceTimersByTime(45_000);

        expect(service.getStatus).toHaveBeenCalledTimes(3);
        expect(fixture.componentInstance.facade.statusLoadFailed()).toBe(true);
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

        fixture.componentInstance.facade.attachToJob('j1', 'ADAPT');
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
        expect(fixture.componentInstance.facade.statusLoadFailed()).toBe(false);

        service.stream$.next({ type: 'PROGRESS', message: 'Still running' });
        expect(fixture.componentInstance.facade.statusLoadFailed()).toBe(false);

        vi.advanceTimersByTime(5_000);
        expect(service.getStatus).toHaveBeenCalledTimes(2);
        expect(fixture.componentInstance.facade.statusLoadFailed()).toBe(false);
    });
    it('does not globally lock a caller-owned run after status retries are exhausted', () => {
        vi.useFakeTimers();
        const fixture = createWith(null);
        service.getStatus = vi.fn(() => throwError(() => new Error('authoritative status unavailable')));

        fixture.componentInstance.facade.attachToJob('j1', 'ADAPT');
        expect(fixture.componentInstance.facade.statusLoadFailed()).toBe(false);

        vi.advanceTimersByTime(1_000);
        expect(fixture.componentInstance.facade.statusLoadFailed()).toBe(false);

        vi.advanceTimersByTime(2_000);
        expect(fixture.componentInstance.facade.statusLoadFailed()).toBe(false);

        service.stream$.next({ type: 'PROGRESS', message: 'Still running' });
        expect(fixture.componentInstance.facade.statusLoadFailed()).toBe(false);
    });
    it('releases the manual editor lock when retained-status requests never respond', () => {
        vi.useFakeTimers();
        service.getStatus = vi.fn(() => new Subject<HyperionGenerationStatus | null>());
        const fixture = createWith(null);

        vi.advanceTimersByTime(18_000);

        expect(service.getStatus).toHaveBeenCalledTimes(3);
        expect(fixture.componentInstance.facade.statusLoading()).toBe(false);
        expect(fixture.componentInstance.facade.statusLoadFailed()).toBe(true);
    });
    it('does not globally lock a caller-owned terminal event when status remains unavailable', () => {
        vi.useFakeTimers();
        const fixture = createWith(null);
        service.getStatus = vi.fn(() => throwError(() => new Error('temporary failure')));
        fixture.componentInstance.facade.attachToJob('j1', 'ADAPT');
        vi.advanceTimersByTime(3_000);
        expect(fixture.componentInstance.facade.statusLoadFailed()).toBe(false);

        service.stream$.next({
            type: 'DONE',
            verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 1, reasons: [] },
            liveExerciseChanged: true,
        });

        expect(fixture.componentInstance.facade.statusLoadFailed()).toBe(false);
        expect(fixture.componentInstance.facade.statusLoading()).toBe(false);
    });
    it('keeps caller-owned terminal state usable when its authoritative refresh fails', () => {
        const fixture = createWith(null);
        const pendingStatus = new Subject<HyperionGenerationStatus | null>();
        service.getStatus = vi.fn(() => pendingStatus);
        fixture.componentInstance.facade.attachToJob('j1', 'ADAPT');

        service.stream$.next({
            type: 'DONE',
            verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 1, reasons: [] },
            liveExerciseChanged: true,
        });
        pendingStatus.error(new Error('late failure'));

        expect(fixture.componentInstance.facade.statusLoadFailed()).toBe(false);
        expect(fixture.componentInstance.facade.running()).toBe(false);
    });
    it('clears any status failure when a matched terminal refresh succeeds', () => {
        const fixture = createWith({ jobId: 'j1', mode: 'ADAPT', running: true, events: [], fileChanges: [] });
        const terminalStatus = new Subject<HyperionGenerationStatus | null>();
        const revertStatus = new Subject<HyperionGenerationStatus | null>();
        service.getStatus = vi.fn().mockReturnValueOnce(terminalStatus).mockReturnValueOnce(revertStatus);

        service.stream$.next({ type: 'DONE', completionStatus: 'SUCCESS', liveExerciseChanged: true });
        terminalStatus.error(new Error('terminal reconciliation failed'));
        fixture.componentInstance.facade.statusLoadFailed.set(true);

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

        expect(fixture.componentInstance.facade.statusLoadFailed()).toBe(false);
    });
    it('does not update status after the component is destroyed', () => {
        vi.useFakeTimers();
        const pendingStatus = new Subject<HyperionGenerationStatus | null>();
        service.getStatus = vi.fn(() => pendingStatus);
        const fixture = createWith(null);
        const component = fixture.componentInstance.facade;

        fixture.destroy();
        pendingStatus.error(new Error('late status failure'));
        vi.advanceTimersByTime(60_000);

        expect(service.getStatus).toHaveBeenCalledOnce();
        expect(component.statusLoadFailed()).toBe(false);
    });
    it('coalesces live fileChanges by repository and path', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [fileChange('solution/A.java', 'write')] });
        const component = fixture.componentInstance.facade;

        service.stream$.next(fileChange('solution/A.java', 'edit', { turn: 2 }));
        expect(component.fileChanges()).toHaveLength(1);
        expect(component.fileChanges()[0].turn).toBe(2);

        service.stream$.next(fileChange('solution/A.java', 'edit', { turn: 1 }));
        expect(component.fileChanges()[0].turn).toBe(2);

        service.stream$.next(fileChange('template/B.java', 'write'));
        expect(component.fileChanges()).toHaveLength(2);
    });
    it('reconciles cancellation status a bounded number of times when the terminal stream event stalls', () => {
        vi.useFakeTimers();
        service.status = { jobId: 'j1', mode: 'GENERATE', running: true, events: [], fileChanges: [] };
        const fixture = createWith(service.status);
        const statusSpy = vi.spyOn(service, 'getStatus');

        fixture.componentInstance.facade.cancel();
        vi.advanceTimersByTime(10_000);

        expect(statusSpy).toHaveBeenCalled();
        expect(fixture.componentInstance.facade.cancelRequested()).toBe(true);

        const callsAfterInitialReconciliation = statusSpy.mock.calls.length;
        vi.advanceTimersByTime(10_000);
        expect(statusSpy.mock.calls.length).toBeGreaterThan(callsAfterInitialReconciliation);

        fixture.componentInstance.facade.cancel();
        expect(service.cancelCalls).toEqual([[42, 'j1']]);
    });
    it('clears stale running state when cancellation status has no retained job', () => {
        vi.useFakeTimers();
        const fixture = createWith({ jobId: 'j1', mode: 'GENERATE', running: true, events: [], fileChanges: [] });
        service.status = null;

        fixture.componentInstance.facade.cancel();
        vi.advanceTimersByTime(1_000);

        expect(fixture.componentInstance.facade.cancelRequested()).toBe(false);
        expect(fixture.componentInstance.facade.running()).toBe(false);
        expect(fixture.componentInstance.facade.jobId()).toBeUndefined();
    });
    it('adopts a newer job when cancellation reconciliation returns a different job', () => {
        vi.useFakeTimers();
        const fixture = createWith({ jobId: 'j1', mode: 'GENERATE', running: true, events: [], fileChanges: [] });
        service.status = { jobId: 'j2', mode: 'ADAPT', running: true, events: [], fileChanges: [] };

        fixture.componentInstance.facade.cancel();
        vi.advanceTimersByTime(1_000);

        expect(fixture.componentInstance.facade.cancelRequested()).toBe(false);
        expect(fixture.componentInstance.facade.jobId()).toBe('j2');
        expect(fixture.componentInstance.facade.mode()).toBe('ADAPT');
        expect(fixture.componentInstance.facade.running()).toBe(true);
    });
    it('caps retained progress events to the latest entries', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });
        const component = fixture.componentInstance.facade;

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
        const component = fixture.componentInstance.facade;

        expect(component.events()).toHaveLength(50);
        expect(component.events()[0]?.message).toBe('event 5');
        expect(component.events()[49]?.message).toBe('event 54');
    });
    it('records the terminal verdict from the stream', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });
        const component = fixture.componentInstance.facade;
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

        expect(fixture.componentInstance.facade.fileChanges()).toHaveLength(1);
        expect(fixture.componentInstance.facade.fileChanges()[0].turn).toBe(2);
    });
    it('requests cancellation for the owner', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });
        fixture.componentInstance.facade.cancel();
        expect(service.cancelCalls).toEqual([[42, 'j1']]);
        expect(fixture.componentInstance.facade.cancelRequested()).toBe(true);
    });
    it('reports a cancellation request failure and refreshes status', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });
        const alertService = TestBed.inject(AlertService);
        const errorSpy = vi.spyOn(alertService, 'error');
        const statusSpy = vi.spyOn(service, 'getStatus');
        service.cancel = vi.fn(() => throwError(() => new HttpErrorResponse({ status: 503 })));

        fixture.componentInstance.facade.cancel();

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.hyperion.generationActivity.cancelFailed');
        expect(statusSpy).toHaveBeenCalledWith(42);
        expect(fixture.componentInstance.facade.cancelRequested()).toBe(false);
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
        const component = fixture.componentInstance.facade;
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

        fixture.componentInstance.facade.cancel();

        expect(service.cancelCalls).toEqual([[42, 'j1']]);
        expect(fixture.componentInstance.facade.cancelRequested()).toBe(false);
        expect(fixture.componentInstance.facade.running()).toBe(false);
        expect(fixture.componentInstance.facade.events()).toEqual([{ type: 'CANCELLED', message: 'Generation was cancelled. Nothing was changed.', timestamp: '' }]);
        expect(errorSpy).not.toHaveBeenCalled();
    });
    it('uses a sanitized terminal outcome instead of inferring that another instructor changed the exercise', () => {
        vi.useFakeTimers();
        const fixture = createWith({ jobId: 'other-job', mode: 'GENERATE', running: true, ownedByCaller: false, events: [], fileChanges: [] });
        const completed = vi.fn();
        fixture.componentInstance.facade.generationCompleted.subscribe(completed);
        expect(fixture.componentInstance.facade.running()).toBe(true);

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
        expect(fixture.componentInstance.facade.running()).toBe(false);
        expect(fixture.componentInstance.facade.jobId()).toBe('other-job');
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

        expect(fixture.componentInstance.facade.ownedByCaller()).toBe(false);
        expect(fixture.componentInstance.facade.cancellable()).toBe(false);
        expect(subscribeToStream).not.toHaveBeenCalled();
        fixture.componentInstance.facade.cancel();
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
        expect(fixture.componentInstance.facade.running()).toBe(false);
        expect(fixture.componentInstance.facade.completionStatus()).toBe('SUCCESS');
    });
    it('replays retained status immediately after attaching to avoid missing early stream events', () => {
        const fixture = createWith(null);
        const component = fixture.componentInstance.facade;
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
    it.each(['success', 'partial', 'failure'])('ignores an old exercise undo %s after navigation', (outcome) => {
        const fixture = createWith({ jobId: 'old', fileChanges: [], running: false, events: [], revertAvailable: true });
        const component = fixture.componentInstance.facade;
        const response = new Subject<ExerciseGenerationRevertResult>();
        vi.spyOn(service, 'revertExerciseGeneration').mockReturnValue(response);
        const errorAlert = vi.spyOn(TestBed.inject(AlertService), 'error');
        const reverted = vi.fn();
        component.generationReverted.subscribe(reverted);
        component.acceptRevert();

        service.status = { jobId: 'current', fileChanges: [], running: true, events: [{ type: 'PROGRESS', message: 'Current exercise' }] };
        fixture.componentRef.setInput('exerciseId', 43);
        fixture.detectChanges();
        if (outcome === 'success') {
            response.next({ fullyReverted: true, revertedRepositories: ['template'], completedAt: '2026-07-10T20:00:00Z' });
        } else {
            response.error(
                new HttpErrorResponse({
                    status: outcome === 'partial' ? 409 : 500,
                    error: { fullyReverted: false, revertedRepositories: ['template'], completedAt: '2026-07-10T20:00:00Z' },
                }),
            );
        }
        expect(component.jobId()).toBe('current');
        expect(component.running()).toBe(true);
        expect(component.events().at(-1)?.message).toBe('Current exercise');
        expect(reverted).not.toHaveBeenCalled();
        expect(component.revertPartialRepositories()).toBeUndefined();
        expect(errorAlert).not.toHaveBeenCalled();
    });
    it('clears pending undo state when another job replaces the observed run', () => {
        const fixture = createWith({ jobId: 'old', fileChanges: [], running: false, events: [], revertAvailable: true });
        const component = fixture.componentInstance.facade;
        const response = new Subject<ExerciseGenerationRevertResult>();
        vi.spyOn(service, 'revertExerciseGeneration').mockReturnValue(response);
        component.acceptRevert();
        expect(component.reverting()).toBe(true);
        service.status = { jobId: 'current', fileChanges: [], running: true, events: [] };
        service.exerciseState$.next({ exerciseId: 42, jobId: 'current', running: true });
        expect(component.reverting()).toBe(false);
        response.next({ fullyReverted: true, revertedRepositories: ['template'], completedAt: '2026-07-10T20:00:00Z' });
        expect(component.jobId()).toBe('current');
        expect(component.running()).toBe(true);
    });
    it.each(['success', 'failure'])('ignores an old cancellation %s after navigation', async (outcome) => {
        vi.useFakeTimers();
        const fixture = createWith({ jobId: 'old', fileChanges: [], running: true, events: [] });
        const response = new Subject<void>();
        vi.spyOn(service, 'cancel').mockReturnValue(response);
        fixture.componentInstance.facade.cancel();
        service.status = { jobId: 'current', fileChanges: [], running: true, events: [] };
        fixture.componentRef.setInput('exerciseId', 43);
        fixture.detectChanges();
        await vi.advanceTimersByTimeAsync(0);
        const status = vi.spyOn(service, 'getStatus');
        if (outcome === 'success') {
            response.next();
        } else {
            response.error(new HttpErrorResponse({ status: 500 }));
        }
        await vi.advanceTimersByTimeAsync(1000);
        expect(status).not.toHaveBeenCalled();
        expect(fixture.componentInstance.facade.jobId()).toBe('current');
        expect(fixture.componentInstance.facade.cancelRequested()).toBe(false);
    });
    it('refreshes after an incomplete metadata-only undo even when no repositories were reset', () => {
        const fixture = createWith(null);
        const component = fixture.componentInstance.facade;
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
        expect(reverted).toHaveBeenCalledExactlyOnceWith('2026-07-10T20:00:00Z');
    });
    it('does not offer revert for a mechanically verified generate run', () => {
        const fixture = createWith(null);
        const component = fixture.componentInstance.facade;

        component.attachToJob('j9', 'GENERATE');
        service.stream$.next({ type: 'DONE', verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] } });
        expect(component.canRevert()).toBe(false);
    });
    it('does not offer revert when a mechanically verified adaptation was only partially saved', () => {
        const fixture = createWith(null);
        const component = fixture.componentInstance.facade;

        component.attachToJob('j9', 'ADAPT');
        service.stream$.next({
            type: 'DONE',
            completionStatus: 'PARTIAL',
            verdict: { mechanicallyVerified: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] },
            liveExerciseChanged: false,
        });

        expect(component.canRevert()).toBe(false);
    });
    it('refreshes authoritative status instead of pretending completion when the live stream errors', () => {
        vi.useFakeTimers();
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileChanges: [] });
        const component = fixture.componentInstance.facade;
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
        const component = fixture.componentInstance.facade;
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
        const component = fixture.componentInstance.facade;
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
        const component = fixture.componentInstance.facade;
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
        const component = fixture.componentInstance.facade;

        service.stream$.next({ type: 'CANCELLED', message: 'Cancelled by user' });
        expect(component.running()).toBe(false);
        expect(component.verdict()).toBeUndefined();
        expect(component.canRevert()).toBe(false);
    });
    it('refreshes a prior adaptation undo after a later generation stops', () => {
        vi.useFakeTimers();
        const fixture = createWith(null);
        const component = fixture.componentInstance.facade;
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
        const component = fixture.componentInstance.facade;

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
        const component = fixture.componentInstance.facade;

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
        const component = fixture.componentInstance.facade;

        component.attachToJob('previous', 'GENERATE');
        pendingStatus.next(normalizeStatus({ jobId: 'current', mode: 'ADAPT', running: true, ownedByCaller: true, events: [], fileChanges: [] }));

        expect(component.jobId()).toBe('current');
        expect(component.mode()).toBe('ADAPT');
        expect(component.running()).toBe(true);
    });
    it('discovers a run started after this editor became idle', () => {
        vi.useFakeTimers();
        const fixture = createWith(null);
        const component = fixture.componentInstance.facade;
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
        const component = fixture.componentInstance.facade;

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
    it('ignores a status response that arrives after the exercise is cleared', () => {
        const pendingStatus = new Subject<HyperionGenerationStatus | null>();
        service.getStatus = () => pendingStatus.asObservable();
        const fixture = createWith(null);
        const component = fixture.componentInstance.facade;

        fixture.componentRef.setInput('exerciseId', undefined);
        fixture.detectChanges();
        pendingStatus.next(normalizeStatus({ jobId: 'stale', running: true, events: [], fileChanges: [] }));

        expect(component.jobId()).toBeUndefined();
        expect(component.running()).toBe(false);
    });
});
