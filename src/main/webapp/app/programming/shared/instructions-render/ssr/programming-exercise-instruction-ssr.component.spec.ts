import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NEVER, Observable, Subject, of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { ProgrammingExerciseInstructionSsrComponent } from 'app/programming/shared/instructions-render/ssr/programming-exercise-instruction-ssr.component';
import { ProblemStatementResultHydrationService } from 'app/programming/shared/instructions-render/ssr/problem-statement-result-hydration.service';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { ResultService } from 'app/exercise/result/result.service';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockResultService } from 'test/helpers/mocks/service/mock-result.service';
import { MockProgrammingExerciseParticipationService } from 'test/helpers/mocks/service/mock-programming-exercise-participation.service';

// The content child renders math through KaTeX; jsdom cannot lay it out.
vi.mock('katex', () => ({ default: { render: vi.fn() } }));

const RENDER_URL_MATCHER = (request: { url: string }) => request.url.endsWith('exercise/problem-statement/render');

const taskSpan = (name: string, testIds: string, status = 'success', notExecutedCount = '0') =>
    `<span class="artemis-task" data-task-name="${name}" data-test-ids="${testIds}" data-test-status="${status}" data-authored-count="1" data-not-executed-count="${notExecutedCount}">${name}</span>`;

/** A response whose stylesheet sits in the body, exactly like the server emits it. */
const bodyStyleResponse = (body: string, contentHash: string) => ({
    html: `<!DOCTYPE html><html><head></head><body><style>.artemis-task{color:red}</style><div class="artemis-problem-statement">${body}</div></body></html>`,
    contentHash,
    rendererVersion: '1.1.0',
});

const passingFeedback = () => [{ testCase: { id: 1, testName: 'testA' }, positive: true }];

describe('ProgrammingExerciseInstructionSsrComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseInstructionSsrComponent>;
    let comp: ProgrammingExerciseInstructionSsrComponent;
    let httpMock: HttpTestingController;
    let resultSubject: Subject<Result>;
    let dialogService: DialogService;
    let currentTheme: ReturnType<typeof signal<Theme>>;
    /** Swappable so a spec can make a *later* hydration fail after an earlier one succeeded. */
    let hydrateResult: (participation: Participation | undefined, result: Result) => Observable<Result>;

    const exercise = { id: 42, problemStatement: '[task][A](<testid>1</testid>)' } as ProgrammingExercise;

    // The script below sits inside `.artemis-problem-statement` on purpose: a script reaching the client within the
    // rendered fragment must never survive into the shadow root, whatever put it there.
    const renderResponse = (status = 'success', extra = '', notExecutedCount = '0') => ({
        html: `<!DOCTYPE html><html><head><style>.artemis-task{color:red}</style></head><body><div class="artemis-problem-statement">${taskSpan('A', '1', status, notExecutedCount)}${extra}<script>window.x=1</script></div></body></html>`,
        contentHash: status + extra + notExecutedCount,
        rendererVersion: '1.1.0',
    });

    beforeEach(async () => {
        resultSubject = new Subject<Result>();
        currentTheme = signal<Theme>(Theme.LIGHT);
        hydrateResult = (_participation, result) => of(result);
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
                        withFeedbackDetails: (participation: Participation | undefined, result: Result) => hydrateResult(participation, result),
                        initialResult: () => of(undefined),
                    },
                },
                {
                    provide: ParticipationWebsocketService,
                    useValue: {
                        subscribeForLatestResultOfParticipation: () => resultSubject.asObservable(),
                        unsubscribeForLatestResultOfParticipation: vi.fn(),
                    },
                },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(ProgrammingExerciseInstructionSsrComponent);
        comp = fixture.componentInstance;
        httpMock = TestBed.inject(HttpTestingController);
        dialogService = TestBed.inject(DialogService);
    });

    afterEach(() => httpMock.verify({ ignoreCancelled: true }));

    /** The renderable statement the component produced, parsed so it can be inspected. */
    const frameDocument = (): Document => new DOMParser().parseFromString(comp.renderedHtml() ?? '', 'text/html');

    /**
     * The task indices the component currently declares interactive to the content child.
     *
     * Interactivity is the `[interactive]` input the child receives, which the parent drives from
     * `canOpenFeedback()`, applied only to tasks that carry test ids. The content component's own handling of that
     * input is covered in its spec.
     */
    const interactiveTaskIndices = (): number[] =>
        comp.canOpenFeedback()
            ? comp
                  .tasks()
                  .filter((task) => task.testIds.length)
                  .map((task) => task.index)
            : [];

    const flushRender = (response = renderResponse()) => {
        httpMock.expectOne(RENDER_URL_MATCHER).flush(response);
        fixture.detectChanges();
    };

    /**
     * Fails a render the way the reader actually experiences it. A network error is transient, so the render service
     * retries it once before the failure reaches this component at all; a test that answers only the first attempt
     * leaves the stream pending and never sees a banner.
     */
    const failRenderWithNetworkError = () => {
        vi.useFakeTimers();
        try {
            httpMock.expectOne(RENDER_URL_MATCHER).error(new ProgressEvent('network error'));
            vi.advanceTimersByTime(300);
            httpMock.expectOne(RENDER_URL_MATCHER).error(new ProgressEvent('network error'));
        } finally {
            vi.useRealTimers();
        }
        fixture.detectChanges();
    };

    describe('client-side sanitization', () => {
        // The server sanitizes too, but not all of its output passes that safelist: diagrams and alert icons are
        // injected after it, guarded only by a denylist. These pin the second pass, which the legacy pipeline had.
        const renderedWith = (body: string) => ({
            html: `<!DOCTYPE html><html><head><style>.artemis-task{color:red}</style></head><body><div class="artemis-problem-statement">${body}</div></body></html>`,
            contentHash: body,
            rendererVersion: '1.3.0',
        });

        it.each([
            { case: 'an event handler on an injected svg', body: '<svg onload="window.__x=1"><text>x</text></svg>', gone: 'onload' },
            { case: 'an error handler on an image', body: '<img src="q" onerror="window.__x=1">', gone: 'onerror' },
            { case: 'an inline event handler on a span', body: '<span onmouseover="window.__x=1">hover</span>', gone: 'onmouseover' },
        ])('removes $case before the markup reaches the DOM', ({ body, gone }) => {
            fixture.componentRef.setInput('exercise', exercise);
            fixture.detectChanges();
            flushRender(renderedWith(body));

            expect(comp.renderedHtml()).not.toContain(gone);
            expect(frameDocument().querySelector('[' + gone + ']')).toBeNull();
        });

        it('keeps the whole server vocabulary, so the pass cannot quietly empty the statement', () => {
            const body =
                taskSpan('A', '1') +
                '<svg viewBox="0 0 10 10"><text>diagram</text></svg>' +
                '<div class="markdown-alert markdown-alert-note"><p class="markdown-alert-title">Note</p><p>body</p></div>' +
                '<table><tbody><tr><td>cell</td></tr></tbody></table>' +
                '<pre><code class="language-java">int x;</code></pre>' +
                '<a href="https://example.org">link</a><img src="https://example.org/i.png">' +
                '<span class="katex-formula" data-formula="x^2" data-display-mode="false"></span>';
            fixture.componentRef.setInput('exercise', exercise);
            fixture.detectChanges();
            flushRender(renderedWith(body));

            const root = frameDocument();
            expect(root.querySelectorAll('.artemis-task')).toHaveLength(1);
            expect(root.querySelector('.artemis-task')?.getAttribute('data-test-ids')).toBe('1');
            expect(root.querySelector('svg')).toBeTruthy();
            expect(root.querySelector('.markdown-alert')).toBeTruthy();
            expect(root.querySelector('table td')?.textContent).toBe('cell');
            expect(root.querySelector('pre code')?.className).toContain('language-java');
            expect(root.querySelector('a')?.getAttribute('href')).toBe('https://example.org');
            expect(root.querySelector('img')?.getAttribute('src')).toBe('https://example.org/i.png');
            expect(root.querySelector('.katex-formula')?.getAttribute('data-formula')).toBe('x^2');
            // The task metadata is read from the parsed document, so it has to survive the pass as well.
            expect(comp.tasks()).toHaveLength(1);
        });
    });

    it('drops every server script before the statement reaches the shadow root', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        flushRender();

        expect(comp.renderedHtml()).toContain('artemis-task');
        // No script survives: without an origin boundary the statement shares the page's credentials, so a script in
        // it would run with them. The server's own `<script>` in the fixture is what this guards against.
        expect(frameDocument().querySelectorAll('script')).toHaveLength(0);
        expect(frameDocument().querySelector('.artemis-task')).toBeTruthy();
        // The chrome lives in this component (default encapsulation); only the content child owns a shadow root.
        expect(fixture.nativeElement.shadowRoot).toBeNull();
    });

    it('exposes tasks parsed from server metadata', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        flushRender();

        expect(comp.tasks()).toEqual([{ index: 0, taskName: 'A', testIds: [1], status: 'success', authoredCount: 1, notExecutedCount: 0 }]);
    });

    it('waits for an exercise that is still undefined instead of throwing or reporting "no instructions"', () => {
        const emitted = vi.fn();
        comp.onNoInstructionsAvailable.subscribe(emitted);
        // Several hosts bind a field that is undefined on the first render pass (`signal<ProgrammingExercise>(undefined!)`),
        // which `input.required` does not prevent, so dereferencing it would throw a TypeError inside an effect.
        fixture.componentRef.setInput('exercise', undefined);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('liveUpdates', 'personal');
        fixture.detectChanges();

        httpMock.expectNone(RENDER_URL_MATCHER);
        // "Still loading" and "this exercise has no problem statement" are different states: emitting here would
        // permanently hide the instructions pane in the code editor.
        expect(emitted).not.toHaveBeenCalled();
        expect(comp.isLoading()).toBe(true);
        // Nothing is on screen yet, so this is an initial load and not a refresh of retained content.
        expect(comp.isRefreshing()).toBe(false);

        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        flushRender();

        expect(comp.tasks()).toHaveLength(1);
        expect(comp.isLoading()).toBe(false);
    });

    it('scrolls only the statement, keeping the step wizard and the banners pinned', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        flushRender();

        // The legacy renderer scrolls `.instructions__content__markdown` and leaves its wizard outside that box. Only
        // the content child may sit inside the scroll container here, or the chrome scrolls away with the statement.
        const scrollArea = fixture.nativeElement.querySelector('.ssr-scroll-area');
        expect(scrollArea.querySelector('jhi-programming-exercise-instruction-ssr-content')).toBeTruthy();
        expect(scrollArea.querySelector('jhi-programming-exercise-instruction-ssr-step-wizard')).toBeNull();
        expect(fixture.nativeElement.querySelector('jhi-programming-exercise-instruction-ssr-step-wizard')).toBeTruthy();
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

    it('renders again when a blank problem statement later returns to a previously rendered one', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        flushRender();
        expect(comp.renderedHtml()).toContain('artemis-task');

        fixture.componentRef.setInput('exercise', { id: 42, problemStatement: '' } as ProgrammingExercise);
        fixture.detectChanges();
        expect(comp.renderedHtml()).toBeUndefined();

        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();

        // Served from the render service cache, so the response carries the identical contentHash. Without clearing
        // the retained hash the component would early-return here and stay blank forever.
        httpMock.expectNone(RENDER_URL_MATCHER);
        expect(comp.renderedHtml()).toContain('artemis-task');
        expect(frameDocument().querySelector('.artemis-task')).toBeTruthy();
    });

    it('re-renders when a new result arrives for personal live updates', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('liveUpdates', 'personal');
        fixture.detectChanges();
        flushRender(renderResponse('not-executed'));

        resultSubject.next({ id: 9, feedbacks: passingFeedback() } as Result);
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

    it('subscribes with the personal flag false for shared live updates', () => {
        const wsService = TestBed.inject(ParticipationWebsocketService);
        const spy = vi.spyOn(wsService, 'subscribeForLatestResultOfParticipation');
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('liveUpdates', 'shared');
        fixture.detectChanges();
        flushRender();

        // 'shared' is the exercise-wide staff topic, not the participation owner's own topic: personal must be false.
        expect(spy).toHaveBeenCalledExactlyOnceWith(7, false, exercise.id);
    });

    // Unsubscribing the RxJS stream alone leaves the participation registered in the service, which is what keeps the
    // websocket topic open. The release must name the inputs the subscription was acquired with, not the current ones.
    it('releases the shared websocket registration for the participation it subscribed to', () => {
        const wsService = TestBed.inject(ParticipationWebsocketService);
        const released = vi.spyOn(wsService, 'unsubscribeForLatestResultOfParticipation');
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('liveUpdates', 'personal');
        fixture.detectChanges();
        flushRender();
        expect(released).not.toHaveBeenCalled();

        fixture.componentRef.setInput('participation', { id: 8 });
        fixture.detectChanges();

        expect(released).toHaveBeenCalledExactlyOnceWith(7, exercise);

        fixture.destroy();
        expect(released).toHaveBeenLastCalledWith(8, exercise);
    });

    it('resubscribes with the new mode when liveUpdates changes', () => {
        const wsService = TestBed.inject(ParticipationWebsocketService);
        const spy = vi.spyOn(wsService, 'subscribeForLatestResultOfParticipation');
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('liveUpdates', 'personal');
        fixture.detectChanges();
        flushRender();
        expect(spy).toHaveBeenCalledExactlyOnceWith(7, true, exercise.id);

        fixture.componentRef.setInput('liveUpdates', 'shared');
        fixture.detectChanges();

        expect(spy).toHaveBeenCalledTimes(2);
        expect(spy).toHaveBeenLastCalledWith(7, false, exercise.id);
    });

    it('keeps the previous html when a refresh fails and shows the stale hint in the light DOM', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('liveUpdates', 'personal');
        fixture.detectChanges();
        flushRender();
        const before = comp.renderedHtml();

        resultSubject.next({ id: 9, feedbacks: [{ testCase: { id: 1, testName: 'testA' }, positive: false }] } as Result);
        fixture.detectChanges();
        failRenderWithNetworkError();

        expect(comp.renderedHtml()).toBe(before);
        expect(comp.refreshFailed()).toBe(true);
        expect(comp.initialLoadFailed()).toBe(false);
        // The banner is a tum-ui-message in the light DOM: styles injected into document.head never cross a shadow
        // boundary, so no chrome may live inside the content child.
        const banner = fixture.nativeElement.querySelector('tum-ui-message');
        expect(banner).toBeTruthy();
        expect(banner.getAttribute('data-severity')).toBe('info');
        expect(banner.textContent).toContain('artemisApp.programmingExercise.problemStatement.renderStale');
        // The already rendered statement must still be on screen.
        expect(frameDocument().querySelector('.artemis-task')).toBeTruthy();
    });

    it('selects the rate-limit message when the initial render is rejected with 429', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        httpMock.expectOne(RENDER_URL_MATCHER).flush('too many requests', new HttpErrorResponse({ status: 429, statusText: 'Too Many Requests' }));
        fixture.detectChanges();

        expect(comp.initialLoadFailed()).toBe(true);
        expect(comp.refreshFailed()).toBe(false);
        expect(comp.errorMessageKey()).toBe('artemisApp.programmingExercise.problemStatement.renderRateLimited');
        const banner = fixture.nativeElement.querySelector('tum-ui-message');
        expect(banner.getAttribute('data-severity')).toBe('warn');
        expect(banner.textContent).toContain('artemisApp.programmingExercise.problemStatement.renderRateLimited');
    });

    it('does not reuse a previous render error status for a later hydration failure', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        httpMock.expectOne(RENDER_URL_MATCHER).flush('too many requests', new HttpErrorResponse({ status: 429, statusText: 'Too Many Requests' }));
        fixture.detectChanges();
        expect(comp.errorMessageKey()).toBe('artemisApp.programmingExercise.problemStatement.renderRateLimited');

        hydrateResult = () => throwError(() => new Error('feedback details unavailable'));
        fixture.componentRef.setInput('result', { id: 3 } as Result);
        fixture.detectChanges();

        expect(comp.initialLoadFailed()).toBe(true);
        // The 429 described the render call, not this hydration failure.
        expect(comp.errorStatus()).toBeUndefined();
        expect(comp.errorMessageKey()).toBe('artemisApp.programmingExercise.problemStatement.renderFailed');
    });

    it('keeps the last hydrated result when a refresh hydration fails, so the tasks stay interactive', () => {
        const open = vi.spyOn(dialogService, 'open').mockReturnValue({} as never);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('result', { id: 3, feedbacks: passingFeedback() } as Result);
        fixture.detectChanges();
        flushRender();
        expect(interactiveTaskIndices()).toEqual([0]);

        hydrateResult = () => throwError(() => new Error('feedback details unavailable'));
        fixture.componentRef.setInput('result', { id: 4 } as Result);
        fixture.detectChanges();

        expect(comp.refreshFailed()).toBe(true);
        // Blanking the result would leave a span that is announced and focusable as a button but does nothing.
        expect(comp.canOpenFeedback()).toBe(true);
        expect(interactiveTaskIndices()).toEqual([0]);
        comp.onTaskActivated(0);
        expect(open).toHaveBeenCalledOnce();
    });

    it('opens the feedback dialog with the not-executed count taken from the server metadata', () => {
        const open = vi.spyOn(dialogService, 'open').mockReturnValue({} as never);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('result', { id: 3, feedbacks: passingFeedback() } as Result);
        fixture.detectChanges();
        // The server reports two not-executed tests; the client must forward exactly that, never recompute it.
        flushRender(renderResponse('not-executed', '', '2'));

        comp.onTaskActivated(0);

        expect(open).toHaveBeenCalledOnce();
        expect(open.mock.calls[0][1]?.inputValues?.numberOfNotExecutedTests).toBe(2);
        expect(open.mock.calls[0][1]?.inputValues?.feedbackFilter).toEqual([1]);
    });

    it('opens the feedback dialog for the result the retained markup was rendered from when a refresh fails', () => {
        const open = vi.spyOn(dialogService, 'open').mockReturnValue({} as never);
        const renderedResult = { id: 3, feedbacks: passingFeedback() } as Result;
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('result', renderedResult);
        fixture.componentRef.setInput('liveUpdates', 'personal');
        fixture.detectChanges();
        // Two not-executed tests, so the metadata of this render is distinguishable from the one that never arrives.
        flushRender(renderResponse('not-executed', '', '2'));

        // A newer result hydrates, but its render fails, so the markup and the tasks on screen stay the previous ones.
        resultSubject.next({ id: 9, feedbacks: [{ testCase: { id: 1, testName: 'testA' }, positive: false }] } as Result);
        fixture.detectChanges();
        failRenderWithNetworkError();
        expect(comp.refreshFailed()).toBe(true);

        comp.onTaskActivated(0);

        // Pairing result 9 with these task ids and counts would report the older render's numbers against the newer
        // result, for as long as renders keep failing.
        expect(open).toHaveBeenCalledOnce();
        expect(open.mock.calls[0][1]?.inputValues?.result).toBe(renderedResult);
        expect(open.mock.calls[0][1]?.inputValues?.numberOfNotExecutedTests).toBe(2);
    });

    it('does not open the feedback dialog without a result', () => {
        const open = vi.spyOn(dialogService, 'open');
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        flushRender();

        comp.onTaskActivated(0);

        expect(open).not.toHaveBeenCalled();
    });

    it('makes the tasks interactive when a participation arrives although the render is served from cache', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('result', { id: 3, feedbacks: passingFeedback() } as Result);
        fixture.detectChanges();
        flushRender();
        expect(interactiveTaskIndices()).toEqual([]);

        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.detectChanges();

        // Same markdown, locale, theme and test results: the render service answers from its cache with the identical
        // contentHash, so the DOM is deliberately not replaced. The accessibility gating must update all the same.
        httpMock.expectNone(RENDER_URL_MATCHER);
        expect(interactiveTaskIndices()).toEqual([0]);
    });

    it('keeps the stylesheet the server places inside the body', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        flushRender(bodyStyleResponse(taskSpan('A', '1'), 'body-style'));

        expect(comp.renderedHtml()).toContain('<style>.artemis-task{color:red}</style>');
        expect(frameDocument().querySelector('style')).toBeTruthy();
    });

    it('sends the all-tests-passed signal for a successful result that carries no feedback', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        // The legacy "everything passed" case: no feedback maps to a test input, so the outcome can only travel as
        // its own flag. Without it the server would render neutral tasks for a fully passing submission.
        fixture.componentRef.setInput('result', { id: 3, successful: true, feedbacks: [] } as Result);
        fixture.detectChanges();

        const req = httpMock.expectOne(RENDER_URL_MATCHER);
        expect(req.request.body.allTestsPassed).toBe(true);
        expect(req.request.body.testResults).toBeNull();
        req.flush(renderResponse('success'));
    });

    it('does not claim all tests passed for a result with feedback or an unsuccessful one', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('result', { id: 3, successful: true, feedbacks: passingFeedback() } as Result);
        fixture.detectChanges();

        const withFeedback = httpMock.expectOne(RENDER_URL_MATCHER);
        expect(withFeedback.request.body.allTestsPassed).toBe(false);
        withFeedback.flush(renderResponse('success'));
        fixture.detectChanges();

        // An unsuccessful result without feedback is not "everything passed"; it stays a plain "no result" render.
        fixture.componentRef.setInput('result', { id: 4, successful: false, feedbacks: [] } as Result);
        fixture.detectChanges();

        const withoutFeedback = httpMock.expectOne(RENDER_URL_MATCHER);
        expect(withoutFeedback.request.body.allTestsPassed).toBe(false);
        expect(withoutFeedback.request.body.testResults).toBeNull();
        withoutFeedback.flush(renderResponse('no-result'));
    });

    it('cancels an in-flight render when a newer one supersedes it', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        // Do not flush: while the first request is still open, a new result supersedes it.
        fixture.componentRef.setInput('result', { id: 3, feedbacks: passingFeedback() } as Result);
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

    // A cancelled HttpClient request never delivers a response, so asserting `cancelled` is exactly the assertion
    // that the superseded render can no longer paint the pane. Angular's TestRequest refuses to flush a cancelled
    // request, which is why these specs cannot "deliver the late response" and observe nothing happening.
    it('cancels an in-flight render and keeps waiting when the exercise becomes undefined', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();

        fixture.componentRef.setInput('exercise', undefined);
        fixture.detectChanges();

        const requests = httpMock.match(RENDER_URL_MATCHER);
        expect(requests).toHaveLength(1);
        expect(requests[0].cancelled).toBe(true);
        expect(comp.renderedHtml()).toBeUndefined();
        expect(comp.isLoading()).toBe(true);
        expect(comp.isRefreshing()).toBe(false);
    });

    it('cancels an in-flight render and keeps the rendered statement when the exercise becomes undefined', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        flushRender();
        const before = comp.renderedHtml();

        // The theme switch starts a second render, which is still open when the exercise disappears.
        currentTheme.set(Theme.DARK);
        fixture.detectChanges();
        fixture.componentRef.setInput('exercise', undefined);
        fixture.detectChanges();

        const requests = httpMock.match(RENDER_URL_MATCHER);
        expect(requests).toHaveLength(1);
        expect(requests[0].cancelled).toBe(true);
        expect(comp.renderedHtml()).toBe(before);
        expect(comp.isLoading()).toBe(false);
        expect(comp.isRefreshing()).toBe(true);
    });

    it('drops a render failure banner when the exercise becomes undefined with nothing on screen', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        httpMock.expectOne(RENDER_URL_MATCHER).flush('too many requests', new HttpErrorResponse({ status: 429, statusText: 'Too Many Requests' }));
        fixture.detectChanges();
        expect(comp.initialLoadFailed()).toBe(true);

        fixture.componentRef.setInput('exercise', undefined);
        fixture.detectChanges();

        // The pane has stopped showing an answer for the exercise that error described, so the banner goes with it.
        // A spinner next to a stale failure banner is a state the user must never see.
        expect(comp.isLoading()).toBe(true);
        expect(comp.initialLoadFailed()).toBe(false);
        expect(comp.refreshFailed()).toBe(false);
        expect(comp.errorStatus()).toBeUndefined();
        expect(fixture.nativeElement.querySelector('tum-ui-message')).toBeNull();
    });

    it('drops a stale hint when the exercise becomes undefined with content retained', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        flushRender();
        currentTheme.set(Theme.DARK);
        fixture.detectChanges();
        failRenderWithNetworkError();
        expect(comp.refreshFailed()).toBe(true);

        fixture.componentRef.setInput('exercise', undefined);
        fixture.detectChanges();

        expect(comp.isRefreshing()).toBe(true);
        expect(comp.isLoading()).toBe(false);
        expect(comp.refreshFailed()).toBe(false);
        expect(comp.initialLoadFailed()).toBe(false);
        expect(comp.errorStatus()).toBeUndefined();
        expect(fixture.nativeElement.querySelector('tum-ui-message')).toBeNull();
        // The statement itself is deliberately kept while the next exercise is awaited.
        expect(frameDocument().querySelector('.artemis-task')).toBeTruthy();
    });

    it('drops a render failure banner when the bound exercise changes to a blank problem statement', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        httpMock.expectOne(RENDER_URL_MATCHER).flush('too many requests', new HttpErrorResponse({ status: 429, statusText: 'Too Many Requests' }));
        fixture.detectChanges();
        expect(comp.initialLoadFailed()).toBe(true);

        fixture.componentRef.setInput('exercise', { id: 43, problemStatement: '' } as ProgrammingExercise);
        fixture.detectChanges();

        // The banner described the render of exercise 42, which is no longer bound. Emitting
        // onNoInstructionsAvailable does not help: the host only re-emits it, so nothing hides this pane.
        expect(comp.initialLoadFailed()).toBe(false);
        expect(comp.refreshFailed()).toBe(false);
        expect(comp.errorStatus()).toBeUndefined();
        expect(fixture.nativeElement.querySelector('tum-ui-message')).toBeNull();
        expect(comp.isLoading()).toBe(false);
        expect(comp.isRefreshing()).toBe(false);
    });

    it('cancels an in-flight render and drops the rendered context when the problem statement goes blank', () => {
        const open = vi.spyOn(dialogService, 'open').mockReturnValue({} as never);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('result', { id: 3, feedbacks: passingFeedback() } as Result);
        fixture.detectChanges();
        flushRender();
        const task = comp.tasks()[0];
        expect(comp.canOpenFeedback()).toBe(true);

        currentTheme.set(Theme.DARK);
        fixture.detectChanges();
        fixture.componentRef.setInput('exercise', { id: 42, problemStatement: '' } as ProgrammingExercise);
        fixture.detectChanges();

        const requests = httpMock.match(RENDER_URL_MATCHER);
        expect(requests).toHaveLength(1);
        expect(requests[0].cancelled).toBe(true);
        expect(comp.isLoading()).toBe(false);
        expect(comp.isRefreshing()).toBe(false);
        expect(comp.renderedHtml()).toBeUndefined();
        expect(comp.tasks()).toEqual([]);
        // The result and the participation are still bound, so only the cleared render context stops the removed
        // markup's tasks from still claiming that they can open a dialog.
        expect(comp.canOpenFeedback()).toBe(false);
        comp.openTaskFeedback(task);
        expect(open).not.toHaveBeenCalled();
    });

    it('cancels an in-flight render when the participation changes', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.detectChanges();

        fixture.componentRef.setInput('participation', { id: 8 });
        fixture.detectChanges();

        const requests = httpMock.match(RENDER_URL_MATCHER);
        expect(requests).toHaveLength(2);
        expect(requests[0].cancelled).toBe(true);
        expect(requests[1].cancelled).toBe(false);

        requests[1].flush(renderResponse());
        fixture.detectChanges();
        expect(comp.tasks()).toHaveLength(1);
    });

    it('cancels the in-flight render of the previous participation while hydration for the new one is still pending', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('result', { id: 3, feedbacks: passingFeedback() } as Result);
        fixture.detectChanges();
        // Deliberately left in flight: participation 7's render is still open when the inputs move on.

        hydrateResult = () => NEVER;
        fixture.componentRef.setInput('participation', { id: 8 });
        fixture.detectChanges();

        // The render effect refuses to run while hydration is unsettled, so no new request supersedes the old one.
        // Only cancelling at the start of hydration stops participation 7's response from painting the pane.
        const requests = httpMock.match(RENDER_URL_MATCHER);
        expect(requests).toHaveLength(1);
        expect(requests[0].cancelled).toBe(true);
        expect(comp.renderedHtml()).toBeUndefined();
        expect(comp.isLoading()).toBe(true);
        expect(comp.isRefreshing()).toBe(false);
    });

    it('cancels the in-flight render of the previous participation when hydration for the new one fails', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('result', { id: 3, feedbacks: passingFeedback() } as Result);
        fixture.detectChanges();

        hydrateResult = () => throwError(() => new Error('feedback details unavailable'));
        fixture.componentRef.setInput('participation', { id: 8 });
        fixture.detectChanges();

        // A failed hydration issues no render either, so the same cancellation is the only thing keeping the
        // superseded response off the screen.
        const requests = httpMock.match(RENDER_URL_MATCHER);
        expect(requests).toHaveLength(1);
        expect(requests[0].cancelled).toBe(true);
        expect(comp.renderedHtml()).toBeUndefined();
        expect(comp.initialLoadFailed()).toBe(true);
    });

    it('does not open a dialog carrying the previous participation result when hydration for the new one fails', () => {
        const open = vi.spyOn(dialogService, 'open').mockReturnValue({} as never);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('result', { id: 3, feedbacks: passingFeedback() } as Result);
        fixture.detectChanges();
        flushRender();
        expect(interactiveTaskIndices()).toEqual([0]);

        hydrateResult = () => throwError(() => new Error('feedback details unavailable'));
        fixture.componentRef.setInput('participation', { id: 8 });
        fixture.detectChanges();

        // The stale statement stays on screen, but it was rendered for participation 7 and must not act for 8.
        expect(comp.refreshFailed()).toBe(true);
        expect(comp.renderedHtml()).toBeDefined();
        expect(comp.canOpenFeedback()).toBe(false);
        expect(interactiveTaskIndices()).toEqual([]);
        // The retained markup stays clickable in the browser, so the parent has to guard the activation itself.
        comp.onTaskActivated(0);
        expect(open).not.toHaveBeenCalled();
    });

    it('adopts the new participation when its render is served from the cache', () => {
        const open = vi.spyOn(dialogService, 'open').mockReturnValue({} as never);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('result', { id: 3, feedbacks: passingFeedback() } as Result);
        fixture.detectChanges();
        flushRender();

        fixture.componentRef.setInput('participation', { id: 8 });
        fixture.detectChanges();

        // Identical markdown, locale, theme and test results: the render service answers from its cache with the
        // same contentHash, so the DOM is deliberately kept. It has to belong to participation 8 all the same.
        httpMock.expectNone(RENDER_URL_MATCHER);
        expect(comp.canOpenFeedback()).toBe(true);
        expect(interactiveTaskIndices()).toEqual([0]);
        comp.onTaskActivated(0);
        expect(open.mock.calls[0][1]?.inputValues?.participation).toEqual({ id: 8 });
    });

    it('drops a websocket result that arrives after the bound participation moved on', () => {
        const hydrated: { participationId?: number; resultId?: number }[] = [];
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('liveUpdates', 'personal');
        fixture.detectChanges();
        flushRender();
        hydrateResult = (participation, result) => {
            hydrated.push({ participationId: participation?.id, resultId: result.id });
            return of(result);
        };

        // The bound participation already moved on; the effect that tears the old per-participation subject down
        // only runs on the next change detection pass, so an emission can still slip through here.
        fixture.componentRef.setInput('participation', { id: 8 });
        resultSubject.next({ id: 9, feedbacks: passingFeedback() } as Result);

        // Hydrating would pair participation 8 with participation 7's result.
        expect(hydrated).toEqual([]);
    });

    it('shows the refresh indicator and clears the error flags when hydration restarts over rendered content', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        flushRender();
        fixture.componentRef.setInput('result', { id: 3, feedbacks: passingFeedback() } as Result);
        fixture.detectChanges();
        httpMock.expectOne(RENDER_URL_MATCHER).flush('too many requests', new HttpErrorResponse({ status: 429, statusText: 'Too Many Requests' }));
        fixture.detectChanges();
        expect(comp.refreshFailed()).toBe(true);
        expect(comp.errorStatus()).toBe(429);

        fixture.componentRef.setInput('result', { id: 4, feedbacks: passingFeedback() } as Result);
        fixture.detectChanges();

        expect(comp.isLoading()).toBe(false);
        expect(comp.isRefreshing()).toBe(true);
        expect(comp.refreshFailed()).toBe(false);
        expect(comp.initialLoadFailed()).toBe(false);
        expect(comp.errorStatus()).toBeUndefined();
        httpMock.expectOne(RENDER_URL_MATCHER).flush(renderResponse('success', '<!-- second -->'));
        fixture.detectChanges();
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
});

describe('ProgrammingExerciseInstructionSsrComponent with the real hydration service', () => {
    let fixture: ComponentFixture<ProgrammingExerciseInstructionSsrComponent>;
    let comp: ProgrammingExerciseInstructionSsrComponent;
    let httpMock: HttpTestingController;
    let resultService: ResultService;
    let participationService: ProgrammingExerciseParticipationService;

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
        participationService = TestBed.inject(ProgrammingExerciseParticipationService);
    });

    afterEach(() => httpMock.verify({ ignoreCancelled: true }));

    it('renders with the hydrated latest result of the participation', () => {
        const participation = { id: 7, submissions: [{ id: 10, results: [{ id: 3, feedbacks: passingFeedback() }] }] };
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', participation);
        fixture.detectChanges();

        const request = httpMock.expectOne(RENDER_URL_MATCHER);
        expect(request.request.body.testResults).toEqual([{ testId: 1, testName: 'testA', passed: true }]);
        request.flush({
            html: `<!DOCTYPE html><html><body><div class="artemis-problem-statement">${taskSpan('A', '1')}</div></body></html>`,
            contentHash: 'hydrated',
            rendererVersion: '1.1.0',
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

    it('reports a load failure when the latest result of the participation cannot be fetched', () => {
        // No bound result and no submissions, so hydration goes through the participation lookup. If that failure were
        // swallowed into "there is no result", the statement would silently render with neutral task statuses.
        vi.spyOn(participationService, 'getLatestResultWithFeedback').mockReturnValue(throwError(() => new Error('latest result unavailable')));
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.detectChanges();

        httpMock.expectNone(RENDER_URL_MATCHER);
        expect(comp.initialLoadFailed()).toBe(true);
        expect(comp.isLoading()).toBe(false);
        expect(comp.renderedHtml()).toBeUndefined();
    });

    it('keeps an already rendered statement when a later latest-result fetch fails', () => {
        const getLatestResultSpy = vi.spyOn(participationService, 'getLatestResultWithFeedback').mockReturnValue(of({ id: 3, feedbacks: passingFeedback() } as Result));
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.detectChanges();
        httpMock.expectOne(RENDER_URL_MATCHER).flush({
            html: `<!DOCTYPE html><html><body><div class="artemis-problem-statement">${taskSpan('A', '1')}</div></body></html>`,
            contentHash: 'hydrated',
            rendererVersion: '1.1.0',
        });
        fixture.detectChanges();
        expect(comp.tasks()[0].status).toBe('success');

        getLatestResultSpy.mockReturnValue(throwError(() => new Error('latest result unavailable')));
        fixture.componentRef.setInput('participation', { id: 8 });
        fixture.detectChanges();

        // The stale content survives; only the refresh banner appears.
        httpMock.expectNone(RENDER_URL_MATCHER);
        expect(comp.refreshFailed()).toBe(true);
        expect(comp.initialLoadFailed()).toBe(false);
        expect(comp.renderedHtml()).toBeDefined();
        expect(comp.tasks()[0].status).toBe('success');
    });
});
