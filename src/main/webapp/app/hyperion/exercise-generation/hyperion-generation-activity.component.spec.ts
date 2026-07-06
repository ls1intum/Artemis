import { Component, forwardRef, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpResponse } from '@angular/common/http';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Observable, Subject, of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MonacoEditorComponent } from 'app/editor/monaco-editor/monaco-editor.component';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionGenerationActivityComponent } from 'app/hyperion/exercise-generation/hyperion-generation-activity.component';
import { HyperionFileSnapshot, HyperionGenerationMessage, HyperionGenerationStatus } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

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

    revertAdaptation(exerciseId: number): Observable<void> {
        this.revertCalls.push(exerciseId);
        return of(undefined);
    }

    subscribeToStream(): Observable<HyperionGenerationMessage> {
        return this.stream$.asObservable();
    }
}

function snapshot(path: string, action: 'create' | 'edit', content: string): HyperionFileSnapshot {
    const repo = path.startsWith('solution/') ? 'solution' : path.startsWith('template/') ? 'template' : path.startsWith('tests/') ? 'tests' : 'other';
    return { type: 'FILE_SNAPSHOT', path, repo, action, content, sha256: 'x', bytes: content.length, truncated: false, turn: 1 };
}

describe('HyperionGenerationActivityComponent', () => {
    setupTestBed({ zoneless: true });

    let service: MockService;

    beforeEach(() => {
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

    it('records the terminal verdict from the stream', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [] });
        const component = fixture.componentInstance;

        service.stream$.next({ type: 'DONE', verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 5, reasons: [] } });
        expect(component.running()).toBe(false);
        expect(component.verdict()?.accepted).toBe(true);
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
    });

    it('requests cancellation for the owner', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [] });
        fixture.componentInstance.cancel();
        expect(service.cancelCalls).toEqual([[42, 'j1']]);
        expect(fixture.componentInstance.cancelRequested()).toBe(true);
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

    it('offers revert only for an accepted adapt run and reverts to the captured baseline', () => {
        const fixture = createWith(null);
        const component = fixture.componentInstance;

        component.attachToJob('j9', 'ADAPT');
        // An adapt run offers revert only once it has completed with an accepted verdict; before the DONE event there is nothing to revert to.
        expect(component.canRevert()).toBe(false);

        service.stream$.next({ type: 'DONE', verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 3, reasons: [] } });
        expect(component.running()).toBe(false);
        expect(component.canRevert()).toBe(true);

        component.revert();
        expect(service.revertCalls).toEqual([42]);
        expect(component.reverted()).toBe(true);
        expect(component.canRevert()).toBe(false);
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
});
