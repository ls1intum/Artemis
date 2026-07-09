import { Component, forwardRef, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EMPTY, Observable, Subject, of, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MonacoEditorComponent } from 'app/editor/monaco-editor/monaco-editor.component';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionGenerationActivityComponent } from 'app/hyperion/exercise-generation/hyperion-generation-activity.component';
import {
    ExerciseAdaptationRevertResult,
    ExerciseGenerationFileSnapshot,
    HyperionGenerationMessage,
    HyperionGenerationStatus,
} from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

// A lightweight fake so the read-only preview does not instantiate the real Monaco editor in jsdom. It provides the MonacoEditorComponent DI token so the component's
// viewChild(MonacoEditorComponent) RESOLVES to it, exercising the security-critical render effect: we can then assert that snapshot content routes to the text-only
// changeModel() sink (never innerHTML), and that changed lines create a decorations collection.
@Component({
    selector: 'jhi-monaco-editor',
    template: '',
    providers: [{ provide: MonacoEditorComponent, useExisting: forwardRef(() => FakeMonacoEditorComponent) }],
})
class FakeMonacoEditorComponent {
    readOnly = input(false);
    shrinkToFit = input(true);
    changeModel = vi.fn();
    decorationsCollection = { clear: vi.fn() };
    createDecorationsCollection = vi.fn(() => this.decorationsCollection);
    getEditor = vi.fn(() => ({ createDecorationsCollection: this.createDecorationsCollection }));
}

class MockService {
    status: HyperionGenerationStatus | null = null;
    stream$ = new Subject<HyperionGenerationMessage>();
    cancelCalls: [number, string][] = [];

    getStatus() {
        return of(new HttpResponse<HyperionGenerationStatus>({ body: this.status }));
    }

    cancel(exerciseId: number, jobId: string): Observable<void> {
        this.cancelCalls.push([exerciseId, jobId]);
        return of(undefined);
    }

    revertCalls: number[] = [];

    revertAdaptation(exerciseId: number): Observable<ExerciseAdaptationRevertResult> {
        this.revertCalls.push(exerciseId);
        return of({ fullyReverted: true, revertedRepositories: ['TEMPLATE', 'SOLUTION', 'TESTS'] });
    }

    subscribeToStream(): Observable<HyperionGenerationMessage> {
        return this.stream$.asObservable();
    }
}

function snapshot(path: string, action: 'create' | 'edit', content: string, overrides: Partial<ExerciseGenerationFileSnapshot> = {}): ExerciseGenerationFileSnapshot {
    const repo = path.startsWith('solution/') ? 'solution' : path.startsWith('template/') ? 'template' : path.startsWith('tests/') ? 'tests' : 'other';
    return { type: 'FILE_SNAPSHOT', path, repo, action, content, sha256: 'x', bytes: content.length, truncated: false, turn: 1, ...overrides };
}

describe('HyperionGenerationActivityComponent', () => {
    setupTestBed({ zoneless: true });

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
        TestBed.overrideComponent(HyperionGenerationActivityComponent, {
            remove: { imports: [MonacoEditorComponent] },
            add: { imports: [FakeMonacoEditorComponent] },
        });
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    function createWith(status: HyperionGenerationStatus | null) {
        service.status = status;
        const fixture = TestBed.createComponent(HyperionGenerationActivityComponent);
        fixture.componentRef.setInput('exerciseId', 42);
        fixture.detectChanges();
        return fixture;
    }

    it('self-hides when there is no retained run', () => {
        const fixture = createWith(null);
        expect(fixture.componentInstance.visible()).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-activity"]')).toBeNull();
    });

    it('rehydrates the preview from the status and follows the latest file', () => {
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
        expect(component.activeSnapshot()?.path).toBe('tests/T.java');
    });

    it('folds a live snapshot, coalescing by path, and pins on selection', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [snapshot('solution/A.java', 'create', 'a')] });
        const component = fixture.componentInstance;

        service.stream$.next(snapshot('solution/A.java', 'edit', 'a2'));
        expect(component.snapshots()).toHaveLength(1);
        expect(component.activeSnapshot()?.content).toBe('a2');

        service.stream$.next(snapshot('template/B.java', 'create', 'b'));
        expect(component.snapshots()).toHaveLength(2);
        expect(component.activeSnapshot()?.path).toBe('template/B.java');

        component.selectFile('solution/A.java');
        expect(component.follow()).toBe(false);
        expect(component.activePath()).toBe('solution/A.java');
    });

    it('keeps same-path snapshots separate across repositories', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [] });
        const component = fixture.componentInstance;

        service.stream$.next(snapshot('src/Main.java', 'create', 'template', { repo: 'template' }));
        service.stream$.next(snapshot('src/Main.java', 'create', 'solution', { repo: 'solution' }));

        expect(component.snapshots()).toHaveLength(2);
        expect(component.filesByRepo().map((group) => [group.repo, group.files.map((file) => file.content)])).toEqual([
            ['solution', ['solution']],
            ['template', ['template']],
        ]);

        component.selectFile('src/Main.java', 'template');
        expect(component.activeSnapshot()?.repo).toBe('template');
        expect(component.activeSnapshot()?.content).toBe('template');
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
        });
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-generation-completion-status"]')).not.toBeNull();
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
        // The chips must state the actual outcome, not a fixed "Solution passes": a failed solution reads "solution failed" (not the accepted-state label), so colour is not the
        // only signal (a11y). MockTranslateService renders the key, so assert the failed-state key is used and the passed-state key is not.
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

    it('refreshes retained status when cancellation is rejected or already terminal', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [] });
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
        expect(fixture.componentInstance.events()).toEqual([{ type: 'CANCELLED', message: 'Generation was cancelled. Nothing was changed.' }]);
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

        expect(component.events()).toEqual([{ type: 'PROGRESS', message: 'already produced files' }]);
        expect(component.snapshots()).toHaveLength(1);
        expect(component.activeSnapshot()?.path).toBe('solution/A.java');
    });

    it('offers revert only for an accepted adapt run, clears stale preview, and emits refresh hook', () => {
        const fixture = createWith(null);
        const component = fixture.componentInstance;
        const reverted = vi.fn();
        component.adaptationReverted.subscribe(reverted);

        component.attachToJob('j9', 'ADAPT');
        service.stream$.next(snapshot('solution/A.java', 'create', 'adapted'));
        fixture.detectChanges();
        const editor = fixture.debugElement.query(By.directive(FakeMonacoEditorComponent)).componentInstance as FakeMonacoEditorComponent;
        expect(component.activeSnapshot()?.content).toBe('adapted');
        // An adapt run offers revert only once it has completed with an accepted verdict; before the DONE event there is nothing to revert to.
        expect(component.canRevert()).toBe(false);

        service.stream$.next({ type: 'DONE', verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] } });
        expect(component.running()).toBe(false);
        expect(component.canRevert()).toBe(true);

        component.revert();
        expect(service.revertCalls).toEqual([42]);
        expect(component.reverted()).toBe(true);
        expect(component.snapshots()).toEqual([]);
        expect(component.activeSnapshot()).toBeUndefined();
        expect(editor.changeModel).toHaveBeenLastCalledWith('', '');
        expect(reverted).toHaveBeenCalledOnce();
        expect(component.canRevert()).toBe(false);
    });

    it('keeps the preview and revert affordance when the server reports a partial revert', () => {
        const fixture = createWith(null);
        const component = fixture.componentInstance;
        const reverted = vi.fn();
        component.adaptationReverted.subscribe(reverted);
        service.revertAdaptation = (exerciseId: number) => {
            service.revertCalls.push(exerciseId);
            return throwError(() => new HttpErrorResponse({ status: 409, error: { fullyReverted: false, revertedRepositories: ['TEMPLATE'] } }));
        };

        component.attachToJob('j9', 'ADAPT');
        service.stream$.next(snapshot('solution/A.java', 'create', 'adapted'));
        service.stream$.next({ type: 'DONE', verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] } });

        component.revert();

        expect(service.revertCalls).toEqual([42]);
        expect(component.reverted()).toBe(false);
        expect(component.snapshots()).toHaveLength(1);
        expect(reverted).not.toHaveBeenCalled();
        expect(component.canRevert()).toBe(true);
    });

    it('restores the adapt mode on reconnect so the revert affordance survives a reload', () => {
        // A completed, accepted in-place adaptation rehydrated from the status endpoint (not a live attach): mode must come from the status, or the header label and revert are lost.
        const fixture = createWith({
            jobId: 'j5',
            running: false,
            mode: 'ADAPT',
            events: [{ type: 'DONE', verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 4, reasons: [] } }],
            fileSnapshots: [],
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

    it('follows a re-edit of an earlier file, not merely the last-created file', () => {
        // Regression: following must track the last-written path, not array order. upsertSnapshot replaces edited files in place,
        // so a re-edit of an earlier file would otherwise never surface (the last array slot is a different, later-created file).
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [] });
        const component = fixture.componentInstance;

        service.stream$.next(snapshot('solution/A.java', 'create', 'a'));
        service.stream$.next(snapshot('solution/B.java', 'create', 'b'));
        expect(component.activeSnapshot()?.path).toBe('solution/B.java');

        service.stream$.next(snapshot('solution/A.java', 'edit', 'a2'));
        expect(component.snapshots()).toHaveLength(2);
        expect(component.activeSnapshot()?.path).toBe('solution/A.java');
        expect(component.activeSnapshot()?.content).toBe('a2');
    });

    it('routes snapshot content to the text-only changeModel sink and decorates only edits', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [] });
        const editor = fixture.debugElement.query(By.directive(FakeMonacoEditorComponent)).componentInstance as FakeMonacoEditorComponent;

        // A created file: content reaches Monaco via changeModel (never innerHTML), with no diff decorations for a brand-new file.
        service.stream$.next(snapshot('solution/A.java', 'create', 'line1\nline2'));
        fixture.detectChanges();
        expect(editor.changeModel).toHaveBeenCalledWith('solution/A.java', 'line1\nline2');
        expect(editor.createDecorationsCollection).not.toHaveBeenCalled();

        // A re-edit that changes a line: routed through changeModel again, and the changed line is marked via a decorations collection.
        service.stream$.next(snapshot('solution/A.java', 'edit', 'line1\nCHANGED'));
        fixture.detectChanges();
        expect(editor.changeModel).toHaveBeenLastCalledWith('solution/A.java', 'line1\nCHANGED');
        expect(editor.createDecorationsCollection).toHaveBeenCalledTimes(1);
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
        expect(component.events()).toEqual([{ type: 'PROGRESS', message: 'still running' }]);
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
        expect(component.activeSnapshot()?.content).toBe('new');
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
            events: [{ type: 'DONE', verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 3 }, liveExerciseChanged: true }],
            fileSnapshots: [],
        };

        vi.advanceTimersByTime(1_000);

        expect(component.running()).toBe(false);
        expect(component.verdict()?.accepted).toBe(true);
        expect(completed).toHaveBeenCalledExactlyOnceWith({
            mode: undefined,
            verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 3 },
            completionStatus: undefined,
            liveExerciseChanged: true,
        });
    });

    it('stops running on a CANCELLED terminal event from the stream', () => {
        // CANCELLED is a terminal stream event (distinct from the cancel button just requesting it): it stops the run and offers no revert.
        const fixture = createWith({ jobId: 'j1', running: true, mode: 'ADAPT', events: [], fileSnapshots: [] });
        const component = fixture.componentInstance;

        service.stream$.next({ type: 'CANCELLED', message: 'Cancelled by user' });
        expect(component.running()).toBe(false);
        expect(component.verdict()).toBeUndefined();
        expect(component.canRevert()).toBe(false);
    });

    it.each([
        [{ type: 'DONE' as const, completionStatus: 'SUCCESS' as const, verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 3 } }, true],
        [{ type: 'DONE' as const, completionStatus: 'PARTIAL' as const, verdict: { accepted: false, solutionPassed: false, templateFailed: true, testCount: 3 } }, false],
        [{ type: 'CANCELLED' as const, message: 'Cancelled' }, false],
        [{ type: 'ERROR' as const, message: 'Failed' }, false],
    ])('rehydrates terminal status %s from retained status', (terminalEvent, canRevert) => {
        const fixture = createWith({
            jobId: 'j1',
            running: false,
            mode: 'ADAPT',
            events: [terminalEvent],
            fileSnapshots: [snapshot('solution/A.java', 'create', 'a')],
        });
        const component = fixture.componentInstance;

        expect(component.running()).toBe(false);
        expect(component.events().at(-1)).toEqual(terminalEvent);
        expect(component.canRevert()).toBe(canRevert);
        if ('completionStatus' in terminalEvent) {
            expect(component.completionStatus()).toBe(terminalEvent.completionStatus);
        }
    });

    it('does not let a late status response clobber a freshly attached live run', () => {
        // Reconnect race: a status fetch is in flight when the user starts a new run on the same surface. attachToJob bumps the load token, so the
        // late status of the previous (stale) job must be ignored rather than overwrite the live run the user just started.
        const pendingStatus = new Subject<HttpResponse<HyperionGenerationStatus>>();
        service.getStatus = () => pendingStatus.asObservable();
        const fixture = createWith(null);
        const component = fixture.componentInstance;

        component.attachToJob('live', 'GENERATE');
        expect(component.jobId()).toBe('live');
        expect(component.running()).toBe(true);

        pendingStatus.next(new HttpResponse<HyperionGenerationStatus>({ body: { jobId: 'stale', running: false, events: [], fileSnapshots: [] } }));
        expect(component.jobId()).toBe('live');
        expect(component.running()).toBe(true);
    });

    it('merges a late status response without clobbering newer live snapshots or terminal state', () => {
        const pendingStatus = new Subject<HttpResponse<HyperionGenerationStatus>>();
        service.getStatus = () => pendingStatus.asObservable();
        const fixture = createWith(null);
        const component = fixture.componentInstance;

        component.attachToJob('live', 'GENERATE');
        service.stream$.next(snapshot('solution/A.java', 'edit', 'newer'));
        service.stream$.next({ type: 'DONE', verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 1 }, liveExerciseChanged: true });
        pendingStatus.next(
            new HttpResponse<HyperionGenerationStatus>({
                body: {
                    jobId: 'live',
                    running: true,
                    events: [{ type: 'PROGRESS', message: 'older status' }],
                    fileSnapshots: [snapshot('solution/A.java', 'create', 'older')],
                },
            }),
        );

        expect(component.running()).toBe(false);
        expect(component.events().map((event) => event.type)).toContain('DONE');
        expect(component.snapshots()).toHaveLength(1);
        expect(component.snapshots()[0].content).toBe('newer');
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
});
