import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpResponse } from '@angular/common/http';
import { beforeEach, describe, expect, it } from 'vitest';
import { Observable, Subject, of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MonacoEditorComponent } from 'app/editor/monaco-editor/monaco-editor.component';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionGenerationActivityComponent } from 'app/hyperion/exercise-generation/hyperion-generation-activity.component';
import { HyperionFileSnapshot, HyperionGenerationMessage, HyperionGenerationStatus } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

// A selector-matching stub so the read-only Monaco preview does not instantiate the real editor in jsdom. It is NOT a MonacoEditorComponent, so the component's
// viewChild(MonacoEditorComponent) stays undefined and the render effect guards out — exactly the behaviour we want under test.
@Component({ selector: 'jhi-monaco-editor', template: '' })
class StubMonacoEditorComponent {
    readOnly = input(false);
    shrinkToFit = input(true);
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
            add: { imports: [StubMonacoEditorComponent] },
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

        service.stream$.next({ type: 'DONE', completionStatus: 'SUCCESS', verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 5, reasons: [] } });
        expect(component.running()).toBe(false);
        expect(component.verdict()?.accepted).toBe(true);
        expect(component.completionStatus()).toBe('SUCCESS');
    });

    it('requests cancellation for the owner', () => {
        const fixture = createWith({ jobId: 'j1', running: true, events: [], fileSnapshots: [] });
        fixture.componentInstance.cancel();
        expect(service.cancelCalls).toEqual([[42, 'j1']]);
        expect(fixture.componentInstance.cancelRequested()).toBe(true);
    });
});
