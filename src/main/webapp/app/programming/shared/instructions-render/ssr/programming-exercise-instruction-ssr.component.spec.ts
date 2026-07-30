import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Subject, of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import katex from 'katex';
import { ProgrammingExerciseInstructionSsrComponent } from 'app/programming/shared/instructions-render/ssr/programming-exercise-instruction-ssr.component';
import { ProblemStatementResultHydrationService } from 'app/programming/shared/instructions-render/ssr/problem-statement-result-hydration.service';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { ResultService } from 'app/exercise/result/result.service';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockResultService } from 'test/helpers/mocks/service/mock-result.service';
import { MockProgrammingExerciseParticipationService } from 'test/helpers/mocks/service/mock-programming-exercise-participation.service';

// jsdom cannot lay out math, so KaTeX is mocked. The mock still writes into the passed element so the spec can assert
// that the component handed the placeholder node (and not, say, a detached copy) to the renderer.
vi.mock('katex', () => ({
    default: {
        render: vi.fn((formula: string, element: HTMLElement) => {
            element.innerHTML = `<span class="katex">${formula}</span>`;
        }),
    },
}));

const RENDER_URL_MATCHER = (request: { url: string }) => request.url.endsWith('exercise/problem-statement/render');

const taskSpan = (name: string, testIds: string, status = 'success', notExecutedCount = '0') =>
    `<span class="artemis-task" data-task-name="${name}" data-test-ids="${testIds}" data-test-status="${status}" data-authored-count="1" data-not-executed-count="${notExecutedCount}">${name}</span>`;

/** A response whose stylesheet sits in the body, exactly like the server emits it. */
const bodyStyleResponse = (body: string, contentHash: string) => ({
    html: `<!DOCTYPE html><html><head></head><body><style>.artemis-task{color:red}</style><div class="artemis-problem-statement">${body}</div></body></html>`,
    contentHash,
    rendererVersion: '1.0.0',
});

describe('ProgrammingExerciseInstructionSsrComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseInstructionSsrComponent>;
    let comp: ProgrammingExerciseInstructionSsrComponent;
    let httpMock: HttpTestingController;
    let resultSubject: Subject<Result>;
    let dialogService: DialogService;
    let currentTheme: ReturnType<typeof signal<Theme>>;

    const exercise = { id: 42, problemStatement: '[task][A](<testid>1</testid>)' } as ProgrammingExercise;

    // The server still appends KaTeX scripts even with includeJs=false, and they sit inside the rendered fragment,
    // so the script below is deliberately part of `.artemis-problem-statement` and must be stripped.
    const renderResponse = (status = 'success', extra = '') => ({
        html: `<!DOCTYPE html><html><head><style>.artemis-task{color:red}</style></head><body><div class="artemis-problem-statement">${taskSpan('A', '1', status)}${extra}<script>window.x=1</script></div></body></html>`,
        contentHash: status + extra,
        rendererVersion: '1.0.0',
    });

    beforeEach(async () => {
        resultSubject = new Subject<Result>();
        currentTheme = signal<Theme>(Theme.LIGHT);
        vi.mocked(katex.render).mockClear();
        await TestBed.configureTestingModule({
            imports: [ProgrammingExerciseInstructionSsrComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ThemeService, useValue: { currentTheme } },
                { provide: DialogService, useValue: { open: vi.fn() } },
                {
                    provide: ProblemStatementResultHydrationService,
                    useValue: {
                        withFeedbackDetails: (_participation: unknown, result: Result) => of(result),
                        initialResult: () => of(undefined),
                    },
                },
                {
                    provide: ParticipationWebsocketService,
                    useValue: { subscribeForLatestResultOfParticipation: () => resultSubject.asObservable() },
                },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(ProgrammingExerciseInstructionSsrComponent);
        comp = fixture.componentInstance;
        httpMock = TestBed.inject(HttpTestingController);
        dialogService = TestBed.inject(DialogService);
    });

    afterEach(() => httpMock.verify({ ignoreCancelled: true }));

    const shadowRoot = (): ShadowRoot => fixture.nativeElement.shadowRoot;

    const flushRender = (response = renderResponse()) => {
        httpMock.expectOne(RENDER_URL_MATCHER).flush(response);
        fixture.detectChanges();
    };

    it('renders the server html into the shadow root and strips scripts', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        flushRender();

        expect(comp.renderedHtml()).toContain('artemis-task');
        expect(comp.renderedHtml()).not.toContain('<script');
        expect(shadowRoot()).toBeTruthy();
        expect(shadowRoot().querySelector('.artemis-task')).toBeTruthy();
        expect(shadowRoot().querySelector('script')).toBeNull();
    });

    it('exposes tasks parsed from server metadata', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        flushRender();

        expect(comp.tasks()).toEqual([{ index: 0, taskName: 'A', testIds: [1], status: 'success', authoredCount: 1, notExecutedCount: 0 }]);
    });

    it('emits onNoInstructionsAvailable for an empty problem statement, calls no endpoint and shows no loading indicator', () => {
        const emitted = vi.fn();
        comp.onNoInstructionsAvailable.subscribe(emitted);
        fixture.componentRef.setInput('exercise', { id: 1, problemStatement: '   ' } as ProgrammingExercise);
        fixture.detectChanges();

        httpMock.expectNone(RENDER_URL_MATCHER);
        expect(emitted).toHaveBeenCalled();
        // startHydration switches a loading indicator on before the markdown is known; the empty branch must switch it
        // back off, otherwise an exercise without a problem statement spins forever.
        expect(comp.isLoading()).toBe(false);
        expect(comp.isRefreshing()).toBe(false);
    });

    it('re-renders when a new result arrives for personal live updates', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('liveUpdates', 'personal');
        fixture.detectChanges();
        flushRender(renderResponse('not-executed'));

        resultSubject.next({ id: 9, feedbacks: [{ testCase: { id: 1, testName: 'testA' }, positive: true }] } as Result);
        fixture.detectChanges();
        flushRender(renderResponse('success'));

        expect(comp.tasks()[0].status).toBe('success');
    });

    it('does not subscribe to results when liveUpdates is none', () => {
        const wsService = TestBed.inject(ParticipationWebsocketService);
        const spy = vi.spyOn(wsService, 'subscribeForLatestResultOfParticipation');
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.detectChanges();
        flushRender();

        expect(spy).not.toHaveBeenCalled();
    });

    it('keeps the previous html when a refresh fails and shows the stale hint', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('liveUpdates', 'personal');
        fixture.detectChanges();
        flushRender();
        const before = comp.renderedHtml();

        resultSubject.next({ id: 9, feedbacks: [{ testCase: { id: 1, testName: 'testA' }, positive: false }] } as Result);
        fixture.detectChanges();
        httpMock.expectOne(RENDER_URL_MATCHER).error(new ProgressEvent('network error'));
        fixture.detectChanges();

        expect(comp.renderedHtml()).toBe(before);
        expect(comp.refreshFailed()).toBe(true);
        expect(comp.initialLoadFailed()).toBe(false);
        expect(shadowRoot().textContent).toContain('artemisApp.programmingExercise.problemStatement.renderStale');
        // The already rendered statement must still be on screen.
        expect(shadowRoot().querySelector('.artemis-task')).toBeTruthy();
    });

    it('selects the rate-limit message when the initial render is rejected with 429', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        httpMock.expectOne(RENDER_URL_MATCHER).flush('too many requests', new HttpErrorResponse({ status: 429, statusText: 'Too Many Requests' }));
        fixture.detectChanges();

        expect(comp.initialLoadFailed()).toBe(true);
        expect(comp.refreshFailed()).toBe(false);
        expect(comp.errorMessageKey()).toBe('artemisApp.programmingExercise.problemStatement.renderRateLimited');
        expect(shadowRoot().textContent).toContain('artemisApp.programmingExercise.problemStatement.renderRateLimited');
    });

    it('opens the feedback dialog for a task click when a result and participation exist', () => {
        const open = vi.spyOn(dialogService, 'open').mockReturnValue({} as never);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('result', { id: 3, feedbacks: [{ testCase: { id: 1, testName: 'testA' }, positive: true }] } as Result);
        fixture.detectChanges();
        flushRender();

        comp.openTaskFeedback({ index: 0, taskName: 'A', testIds: [1], status: 'success', authoredCount: 1, notExecutedCount: 0 });

        expect(open).toHaveBeenCalled();
        expect(open.mock.calls[0][1]?.inputValues?.numberOfNotExecutedTests).toBe(0);
    });

    it('does not open the feedback dialog without a result', () => {
        const open = vi.spyOn(dialogService, 'open');
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        flushRender();

        comp.openTaskFeedback({ index: 0, taskName: 'A', testIds: [1], status: 'success', authoredCount: 1, notExecutedCount: 0 });

        expect(open).not.toHaveBeenCalled();
    });

    it('renders the inert katex placeholders emitted by the server', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        flushRender(renderResponse('success', '<span class="katex-formula" data-formula="a^2" data-display-mode="false"></span>'));

        const placeholder = shadowRoot().querySelector('.katex-formula')!;
        expect(katex.render).toHaveBeenCalledOnce();
        expect(vi.mocked(katex.render).mock.calls[0][0]).toBe('a^2');
        expect(vi.mocked(katex.render).mock.calls[0][1]).toBe(placeholder);
        expect(vi.mocked(katex.render).mock.calls[0][2]?.displayMode).toBe(false);
        expect(placeholder.innerHTML).toContain('katex');
    });

    it('keeps the stylesheet the server places inside the body', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        flushRender(bodyStyleResponse(taskSpan('A', '1'), 'body-style'));

        expect(comp.renderedHtml()).toContain('<style>.artemis-task{color:red}</style>');
        expect(shadowRoot().querySelector('style')).toBeTruthy();
    });

    it('cancels an in-flight render when a newer one supersedes it', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        // Do not flush: while the first request is still open, a new result supersedes it.
        fixture.componentRef.setInput('result', { id: 3, feedbacks: [{ testCase: { id: 1, testName: 'testA' }, positive: true }] } as Result);
        fixture.detectChanges();

        const requests = httpMock.match(RENDER_URL_MATCHER);
        expect(requests).toHaveLength(2);
        expect(requests[0].cancelled).toBe(true);
        expect(requests[1].cancelled).toBe(false);
        expect(requests[0].request.body.testResults).toBeNull();
        expect(requests[1].request.body.testResults).toEqual([{ testId: 1, testName: 'testA', passed: true }]);

        requests[1].flush(renderResponse('success'));
        fixture.detectChanges();

        expect(comp.tasks()[0].status).toBe('success');
    });

    it('re-renders when the theme switches to dark', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        const first = httpMock.expectOne(RENDER_URL_MATCHER);
        expect(first.request.body.darkMode).toBe(false);
        first.flush(renderResponse('success'));
        fixture.detectChanges();

        currentTheme.set(Theme.DARK);
        fixture.detectChanges();

        const second = httpMock.expectOne(RENDER_URL_MATCHER);
        expect(second.request.body.darkMode).toBe(true);
        second.flush(renderResponse('success', '<!-- dark -->'));
        fixture.detectChanges();
    });

    it('re-renders when the locale changes', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        const first = httpMock.expectOne(RENDER_URL_MATCHER);
        expect(first.request.body.locale).toBe('en');
        first.flush(renderResponse('success'));
        fixture.detectChanges();

        TestBed.inject(TranslateService).use('de');
        fixture.detectChanges();

        const second = httpMock.expectOne(RENDER_URL_MATCHER);
        expect(second.request.body.locale).toBe('de');
        second.flush(renderResponse('success', '<!-- de -->'));
        fixture.detectChanges();
    });

    it('restores focus to the task at the same index after a re-render', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('result', { id: 3, feedbacks: [{ testCase: { id: 1, testName: 'testA' }, positive: true }] } as Result);
        fixture.detectChanges();
        flushRender(renderResponse('not-executed'));

        const firstTask = shadowRoot().querySelector<HTMLElement>('.artemis-task')!;
        firstTask.focus();
        expect(shadowRoot().activeElement).toBe(firstTask);

        fixture.componentRef.setInput('result', { id: 4, feedbacks: [{ testCase: { id: 1, testName: 'testA' }, positive: false }] } as Result);
        fixture.detectChanges();
        flushRender(renderResponse('fail'));

        const reRenderedTask = shadowRoot().querySelector<HTMLElement>('.artemis-task')!;
        expect(reRenderedTask).not.toBe(firstTask);
        expect(shadowRoot().activeElement).toBe(reRenderedTask);
    });

    it('marks tasks as interactive only when a feedback dialog can be opened', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        flushRender();

        const task = shadowRoot().querySelector<HTMLElement>('.artemis-task')!;
        expect(task.getAttribute('role')).toBeNull();
        expect(task.getAttribute('tabindex')).toBeNull();
        expect(task.getAttribute('aria-label')).toBe('A: artemisApp.programmingExercise.problemStatement.taskStatus.success');

        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('result', { id: 3, feedbacks: [{ testCase: { id: 1, testName: 'testA' }, positive: true }] } as Result);
        fixture.detectChanges();
        flushRender(renderResponse('success', '<!-- with result -->'));

        const interactiveTask = shadowRoot().querySelector<HTMLElement>('.artemis-task')!;
        expect(interactiveTask.getAttribute('role')).toBe('button');
        expect(interactiveTask.getAttribute('tabindex')).toBe('0');
        expect(interactiveTask.getAttribute('aria-label')).toBe('A: artemisApp.programmingExercise.problemStatement.taskStatus.success');
    });

    it('resolves a clicked task by document position, not by name', () => {
        const open = vi.spyOn(dialogService, 'open').mockReturnValue({} as never);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('result', { id: 3, feedbacks: [{ testCase: { id: 1, testName: 'testA' }, positive: true }] } as Result);
        fixture.detectChanges();
        // Two tasks with the same name but different test ids.
        flushRender(bodyStyleResponse(taskSpan('A', '1') + taskSpan('A', '2,3'), 'duplicates'));

        expect(comp.tasks()).toHaveLength(2);
        const secondTask = shadowRoot().querySelectorAll<HTMLElement>('.artemis-task')[1];
        secondTask.dispatchEvent(new MouseEvent('click', { bubbles: true, composed: true }));

        expect(open).toHaveBeenCalledOnce();
        expect(open.mock.calls[0][1]?.inputValues?.feedbackFilter).toEqual([2, 3]);
    });
});

describe('ProgrammingExerciseInstructionSsrComponent with the real hydration service', () => {
    let fixture: ComponentFixture<ProgrammingExerciseInstructionSsrComponent>;
    let comp: ProgrammingExerciseInstructionSsrComponent;
    let httpMock: HttpTestingController;
    let resultService: ResultService;

    const exercise = { id: 42, problemStatement: '[task][A](<testid>1</testid>)' } as ProgrammingExercise;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ProgrammingExerciseInstructionSsrComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ThemeService, useValue: { currentTheme: signal<Theme>(Theme.LIGHT) } },
                { provide: DialogService, useValue: { open: vi.fn() } },
                { provide: ResultService, useClass: MockResultService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                { provide: ParticipationWebsocketService, useValue: { subscribeForLatestResultOfParticipation: () => of(undefined) } },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(ProgrammingExerciseInstructionSsrComponent);
        comp = fixture.componentInstance;
        httpMock = TestBed.inject(HttpTestingController);
        resultService = TestBed.inject(ResultService);
    });

    afterEach(() => httpMock.verify({ ignoreCancelled: true }));

    it('renders with the hydrated latest result of the participation', () => {
        const participation = { id: 7, submissions: [{ id: 10, results: [{ id: 3, feedbacks: [{ testCase: { id: 1, testName: 'testA' }, positive: true }] }] }] };
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', participation);
        fixture.detectChanges();

        const request = httpMock.expectOne(RENDER_URL_MATCHER);
        expect(request.request.body.testResults).toEqual([{ testId: 1, testName: 'testA', passed: true }]);
        request.flush({
            html: `<!DOCTYPE html><html><body><div class="artemis-problem-statement">${taskSpan('A', '1')}</div></body></html>`,
            contentHash: 'hydrated',
            rendererVersion: '1.0.0',
        });
        fixture.detectChanges();

        expect(comp.tasks()[0].status).toBe('success');
        expect(comp.isLoading()).toBe(false);
    });

    it('reports a load failure and renders nothing when hydration fails', () => {
        vi.spyOn(resultService, 'getFeedbackDetailsForResult').mockReturnValue(throwError(() => new Error('feedback details unavailable')));
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        // A result without feedbacks forces a details fetch, which fails.
        fixture.componentRef.setInput('result', { id: 3 } as Result);
        fixture.detectChanges();

        // A hydration failure must never be rendered as "no result": no render request may be issued at all.
        httpMock.expectNone(RENDER_URL_MATCHER);
        expect(comp.initialLoadFailed()).toBe(true);
        expect(comp.isLoading()).toBe(false);
        expect(comp.renderedHtml()).toBeUndefined();
    });
});
