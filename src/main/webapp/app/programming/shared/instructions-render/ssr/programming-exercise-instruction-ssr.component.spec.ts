import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Observable, Subject, of, throwError } from 'rxjs';
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
    rendererVersion: '1.0.0',
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

    // The server still appends KaTeX scripts even with includeJs=false, and they sit inside the rendered fragment,
    // so the script below is deliberately part of `.artemis-problem-statement` and must be stripped.
    const renderResponse = (status = 'success', extra = '', notExecutedCount = '0') => ({
        html: `<!DOCTYPE html><html><head><style>.artemis-task{color:red}</style></head><body><div class="artemis-problem-statement">${taskSpan('A', '1', status, notExecutedCount)}${extra}<script>window.x=1</script></div></body></html>`,
        contentHash: status + extra + notExecutedCount,
        rendererVersion: '1.0.0',
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

    /** The shadow root of the content child. The outer component itself deliberately has none. */
    const contentShadowRoot = (): ShadowRoot => fixture.nativeElement.querySelector('jhi-programming-exercise-instruction-ssr-content').shadowRoot;
    const firstTaskElement = () => contentShadowRoot().querySelector<HTMLElement>('.artemis-task')!;

    const flushRender = (response = renderResponse()) => {
        httpMock.expectOne(RENDER_URL_MATCHER).flush(response);
        fixture.detectChanges();
    };

    it('strips scripts from the rendered html and hands it to the shadow-DOM content child', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        flushRender();

        expect(comp.renderedHtml()).toContain('artemis-task');
        expect(comp.renderedHtml()).not.toContain('<script');
        // The chrome must stay in the light DOM; only the server markup goes behind the shadow boundary.
        expect(fixture.nativeElement.shadowRoot).toBeNull();
        expect(contentShadowRoot().querySelector('.artemis-task')).toBeTruthy();
        expect(contentShadowRoot().querySelector('script')).toBeNull();
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
        // which `input.required` does not prevent. Dereferencing it threw a TypeError inside an effect.
        fixture.componentRef.setInput('exercise', undefined);
        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.componentRef.setInput('liveUpdates', 'personal');
        fixture.detectChanges();

        httpMock.expectNone(RENDER_URL_MATCHER);
        // "Still loading" and "this exercise has no problem statement" are different states: emitting here would
        // permanently hide the instructions pane in the code editor.
        expect(emitted).not.toHaveBeenCalled();
        expect(comp.isLoading()).toBe(true);

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
        expect(firstTaskElement()).toBeTruthy();
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

    it('keeps the previous html when a refresh fails and shows the stale hint in the light DOM', () => {
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
        // The banner is a tum-ui-message in the light DOM: styles injected into document.head never cross a shadow
        // boundary, so no chrome may live inside the content child.
        const banner = fixture.nativeElement.querySelector('tum-ui-message');
        expect(banner).toBeTruthy();
        expect(banner.getAttribute('data-severity')).toBe('info');
        expect(banner.textContent).toContain('artemisApp.programmingExercise.problemStatement.renderStale');
        // The already rendered statement must still be on screen.
        expect(firstTaskElement()).toBeTruthy();
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
        expect(firstTaskElement().getAttribute('role')).toBe('button');

        hydrateResult = () => throwError(() => new Error('feedback details unavailable'));
        fixture.componentRef.setInput('result', { id: 4 } as Result);
        fixture.detectChanges();

        expect(comp.refreshFailed()).toBe(true);
        // Blanking the result would leave a span that is announced and focusable as a button but does nothing.
        expect(comp.canOpenFeedback()).toBe(true);
        expect(firstTaskElement().getAttribute('role')).toBe('button');
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
        expect(firstTaskElement().getAttribute('role')).toBeNull();

        fixture.componentRef.setInput('participation', { id: 7 });
        fixture.detectChanges();

        // Same markdown, locale, theme and test results: the render service answers from its cache with the identical
        // contentHash, so the DOM is deliberately not replaced. The accessibility gating must update all the same.
        httpMock.expectNone(RENDER_URL_MATCHER);
        expect(firstTaskElement().getAttribute('role')).toBe('button');
        expect(firstTaskElement().getAttribute('tabindex')).toBe('0');
    });

    it('keeps the stylesheet the server places inside the body', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        flushRender(bodyStyleResponse(taskSpan('A', '1'), 'body-style'));

        expect(comp.renderedHtml()).toContain('<style>.artemis-task{color:red}</style>');
        expect(contentShadowRoot().querySelector('style')).toBeTruthy();
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
            rendererVersion: '1.0.0',
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
