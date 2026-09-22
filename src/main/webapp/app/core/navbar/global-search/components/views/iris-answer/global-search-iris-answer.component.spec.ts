import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockDirective, MockPipe } from 'ng-mocks';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject, throwError } from 'rxjs';
import { Router, provideRouter } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { faFile, faFilePdf, faFileVideo, faKeyboard, faVideo } from '@fortawesome/free-solid-svg-icons';
import { IrisSearchAnswerService } from 'app/core/navbar/global-search/services/iris-search-answer.service';
import { GlobalSearchIrisAnswerComponent } from './global-search-iris-answer.component';
import { IrisSearchStatusUpdate } from 'app/core/navbar/global-search/models/iris-search-status-update.model';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { SEARCH_DEBOUNCE_MS } from 'app/core/navbar/global-search/components/views/search-result-view.directive';

const SOURCES: LectureSearchResult[] = [
    {
        course: { id: 1, name: 'Course A' },
        lecture: { id: 1, name: 'L1' },
        lectureUnit: { id: 1, name: 'Unit 1', link: '/u/1', pageNumber: 1, sourceType: 'lecture_unit_slide', queryParams: { unit: 1, page: 1 } },
    },
    {
        course: { id: 1, name: 'Course A' },
        lecture: { id: 1, name: 'L1' },
        lectureUnit: { id: 2, name: 'Unit 2', link: '/u/2', pageNumber: 2, sourceType: 'lecture_unit_slide', queryParams: { unit: 2, page: 2 } },
    },
    {
        course: { id: 1, name: 'Course A' },
        lecture: { id: 1, name: 'L1' },
        lectureUnit: { id: 3, name: 'Unit 3', link: '/u/3', pageNumber: 3, sourceType: 'lecture_unit_slide_video', queryParams: { unit: 3, page: 3, timestamp: 42 } },
    },
];

const ENTITY_SOURCES = [
    {
        entityType: 'exercise',
        exerciseType: 'programming',
        entityId: 42,
        course: { id: 9, name: 'Patterns in Software Engineering' },
        title: 'W03E03 Flyweight Pattern',
        snippet: "Programming exercise: 'W03E03 Flyweight Pattern'",
        link: '/courses/9/exercises/42',
    },
];

describe('GlobalSearchIrisAnswerComponent', () => {
    let component: GlobalSearchIrisAnswerComponent;
    let fixture: ComponentFixture<GlobalSearchIrisAnswerComponent>;
    let askSubject: Subject<IrisSearchStatusUpdate>;
    let mockAsk: ReturnType<typeof vi.fn>;

    const originalScrollIntoView = Element.prototype.scrollIntoView;

    /** Drains the word-by-word reveal of a streamed answer, which no single tick would. */
    function revealEverything(): void {
        vi.advanceTimersByTime(5000);
        fixture.detectChanges();
    }

    beforeAll(() => {
        Element.prototype.scrollIntoView = vi.fn();
    });

    afterAll(() => {
        Element.prototype.scrollIntoView = originalScrollIntoView;
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    beforeEach(() => {
        vi.useFakeTimers();
        askSubject = new Subject<IrisSearchStatusUpdate>();
        mockAsk = vi.fn().mockReturnValue(askSubject.asObservable());

        TestBed.configureTestingModule({
            imports: [GlobalSearchIrisAnswerComponent, MockPipe(ArtemisTranslatePipe), MockDirective(MarkdownDirective)],
            providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }, { provide: IrisSearchAnswerService, useValue: { ask: mockAsk } }],
        });

        fixture = TestBed.createComponent(GlobalSearchIrisAnswerComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('searchQuery', '');
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should not render the iris card when there is no result and not thinking', () => {
        const card = fixture.nativeElement.querySelector('.iris-inline-answer');
        expect(card).toBeNull();
    });

    it('should render the iris card when thinking', () => {
        // @ts-expect-error — accessing protected signal for testing
        component.phase.set('thinking');
        fixture.detectChanges();

        const card = fixture.nativeElement.querySelector('.iris-inline-answer');
        expect(card).toBeTruthy();
    });

    it('should pulse the card itself, not just the logo, while thinking', () => {
        // @ts-expect-error
        component.phase.set('thinking');
        fixture.detectChanges();

        const card = fixture.nativeElement.querySelector('.iris-inline-answer');
        expect(card.classList).toContain('is-working');
    });

    it('should stop pulsing the card the moment answering starts, even mid-stream', () => {
        // The arriving text is itself the progress signal once streaming starts; a card still pulsing
        // on top of it would compete with that instead of reinforcing it.
        // @ts-expect-error
        component.phase.set('answering');
        // @ts-expect-error — accessing protected signal for testing
        component.irisResult.set({ answer: 'Signals are reactive primi', sources: [] });
        // @ts-expect-error
        component.progressiveReveal.set(true);
        // @ts-expect-error
        component.streamComplete.set(false);
        fixture.detectChanges();

        expect(component['isStreaming']()).toBe(true); // sanity: genuinely still streaming, not settled
        const card = fixture.nativeElement.querySelector('.iris-inline-answer');
        expect(card.classList).not.toContain('is-working');
    });

    it('should stop pulsing the card once the answer has settled', () => {
        // @ts-expect-error
        component.phase.set('answering');
        // @ts-expect-error — accessing protected signal for testing
        component.irisResult.set({ answer: 'Signals are reactive primitives.', sources: [] });
        fixture.detectChanges();

        const card = fixture.nativeElement.querySelector('.iris-inline-answer');
        expect(card.classList).not.toContain('is-working');
    });

    it('should not pulse the card for a quiet ending', () => {
        // @ts-expect-error
        component.phase.set('noAnswer');
        fixture.detectChanges();

        const card = fixture.nativeElement.querySelector('.iris-inline-answer');
        expect(card.classList).not.toContain('is-working');
    });

    it('should render the strip status while thinking', () => {
        // @ts-expect-error
        component.phase.set('thinking');
        fixture.detectChanges();

        const thinkingWrapper = fixture.nativeElement.querySelector('[data-testid="iris-strip-status"]');
        expect(thinkingWrapper).toBeTruthy();
    });

    it('should fall back to the generic thinking message when no stage has arrived yet', () => {
        // @ts-expect-error
        component.phase.set('thinking');
        fixture.detectChanges();

        const status = fixture.nativeElement.querySelector('[data-testid="iris-strip-status"]');
        expect(status.textContent).toContain('global.search.irisAnswerThinking');
    });

    it('should show the searching stage message once the server reports that stage', () => {
        // @ts-expect-error
        component.phase.set('thinking');
        // @ts-expect-error — accessing private signal for testing
        component.stage.set('searching');
        fixture.detectChanges();

        const status = fixture.nativeElement.querySelector('[data-testid="iris-strip-status"]');
        expect(status.textContent).toContain('global.search.irisAnswerStageSearching');
    });

    it('should show the generating stage message once the server reports that stage', () => {
        // @ts-expect-error
        component.phase.set('thinking');
        // @ts-expect-error
        component.stage.set('generating');
        fixture.detectChanges();

        const status = fixture.nativeElement.querySelector('[data-testid="iris-strip-status"]');
        expect(status.textContent).toContain('global.search.irisAnswerStageGenerating');
    });

    it('should fall back to the searching message for a found stage with no sources yet', () => {
        // Should not happen in practice (the pipeline only fires 'found' once retrieval has
        // something), but a defensively-empty list must not render an empty/broken message.
        // @ts-expect-error
        component.phase.set('thinking');
        // @ts-expect-error
        component.stage.set('found');
        fixture.detectChanges();

        expect(component['stageStatusKey']()).toBe('global.search.irisAnswerStageSearching');
        expect(component['stageStatusParams']()).toEqual({});
    });

    it('should name the courses found when 1-2 were found', () => {
        // @ts-expect-error
        component.phase.set('thinking');
        // @ts-expect-error
        component.stage.set('found');
        // @ts-expect-error — accessing private signal for testing
        component.stageSources.set(['Advanced Algorithms', 'Software Engineering']);
        fixture.detectChanges();

        expect(component['stageStatusKey']()).toBe('global.search.irisAnswerStageFoundSources');
        expect(component['stageStatusParams']()).toEqual({ summary: 'Advanced Algorithms, Software Engineering' });
    });

    it('should name the first two courses and count the rest when more than 2 were found', () => {
        // @ts-expect-error
        component.phase.set('thinking');
        // @ts-expect-error
        component.stage.set('found');
        // @ts-expect-error
        component.stageSources.set(['Advanced Algorithms', 'Software Engineering', 'Databases', 'Networks']);
        fixture.detectChanges();

        expect(component['stageStatusKey']()).toBe('global.search.irisAnswerStageFoundSourcesAndMore');
        expect(component['stageStatusParams']()).toEqual({ summary: 'Advanced Algorithms, Software Engineering', extra: 2 });
    });

    it('should show the plain generating message even when course names are still set from a prior found stage', () => {
        // 'generating' never names sources itself — 'found' already said what was found, so
        // repeating it here would read as a mismatch between the word "generating" and course
        // names, which is exactly the phrasing a live run was reported not to like.
        // @ts-expect-error
        component.phase.set('thinking');
        // @ts-expect-error
        component.stage.set('generating');
        // @ts-expect-error
        component.stageSources.set(['Advanced Algorithms', 'Software Engineering']);
        fixture.detectChanges();

        expect(component['stageStatusKey']()).toBe('global.search.irisAnswerStageGenerating');
    });

    it('should fall back to the generic message for an unrecognized stage value', () => {
        // Forward-compatible: a future stage name this client does not know about yet must
        // still show something readable rather than a raw, untranslated key or blank text.
        // @ts-expect-error
        component.phase.set('thinking');
        // @ts-expect-error
        component.stage.set('reranking');
        fixture.detectChanges();

        const status = fixture.nativeElement.querySelector('[data-testid="iris-strip-status"]');
        expect(status.textContent).toContain('global.search.irisAnswerThinking');
    });

    it('should not render the strip status once an answer exists', () => {
        // @ts-expect-error
        component.phase.set('answering');
        // @ts-expect-error — accessing protected signal for testing
        component.irisResult.set({ answer: 'Some answer', sources: [] });
        fixture.detectChanges();

        const thinkingWrapper = fixture.nativeElement.querySelector('[data-testid="iris-strip-status"]');
        expect(thinkingWrapper).toBeNull();
    });

    it('should render the answer text region when irisResult has an answer', () => {
        // @ts-expect-error
        component.phase.set('answering');
        // @ts-expect-error — accessing protected signal for testing
        component.irisResult.set({ answer: 'Signals are reactive primitives.', sources: [] });
        fixture.detectChanges();

        const answerEl = fixture.nativeElement.querySelector('.iris-answer-text');
        expect(answerEl).toBeTruthy();
    });

    it('should apply is-clamped class when shouldClamp is true', () => {
        // @ts-expect-error
        component.phase.set('answering');
        // @ts-expect-error — accessing protected signal for testing
        component.irisResult.set({ answer: 'Long answer.', sources: [] });
        fixture.detectChanges(); // effect runs, resets isOverflowing=false
        // Set isOverflowing AFTER effect ran — changing it does not re-trigger the effect
        // @ts-expect-error
        component.isOverflowing.set(true);
        fixture.detectChanges();

        const answerEl = fixture.nativeElement.querySelector('.iris-answer-text');
        expect(answerEl.classList).toContain('is-clamped');
    });

    it('should apply is-expanded class when isOverflowing and isExpanded are both true', () => {
        // @ts-expect-error
        component.phase.set('answering');
        // @ts-expect-error — accessing protected signal for testing
        component.irisResult.set({ answer: 'Long answer.', sources: [] });
        fixture.detectChanges(); // effect runs, resets isOverflowing/isExpanded to false
        // @ts-expect-error
        component.isOverflowing.set(true);
        // @ts-expect-error
        component.isExpanded.set(true);
        fixture.detectChanges();

        const answerEl = fixture.nativeElement.querySelector('.iris-answer-text');
        expect(answerEl.classList).toContain('is-expanded');
    });

    it('should show the "show more" toggle button when the answer overflows', () => {
        // @ts-expect-error
        component.phase.set('answering');
        // @ts-expect-error — accessing protected signal for testing
        component.irisResult.set({ answer: 'Long answer text here.', sources: [] });
        fixture.detectChanges(); // effect runs, resets isOverflowing
        // @ts-expect-error
        component.isOverflowing.set(true);
        fixture.detectChanges();

        const toggleBtn = fixture.nativeElement.querySelector('.iris-toggle-btn');
        expect(toggleBtn).toBeTruthy();
    });

    it('should expand the answer when the "show more" toggle button is clicked', () => {
        // @ts-expect-error
        component.phase.set('answering');
        // @ts-expect-error — accessing protected signal for testing
        component.irisResult.set({ answer: 'Long answer.', sources: [] });
        fixture.detectChanges(); // effect runs, resets isOverflowing
        // @ts-expect-error
        component.isOverflowing.set(true);
        fixture.detectChanges();

        const toggleBtn = fixture.nativeElement.querySelector('.iris-toggle-btn');
        toggleBtn.click();
        fixture.detectChanges();

        expect(component['isExpanded']()).toBe(true);
    });

    it('should show the "show less" toggle button when expanded', () => {
        // @ts-expect-error
        component.phase.set('answering');
        // @ts-expect-error — accessing protected signal for testing
        component.irisResult.set({ answer: 'Long answer.', sources: [] });
        fixture.detectChanges(); // effect runs, resets isOverflowing/isExpanded
        // @ts-expect-error
        component.isOverflowing.set(true);
        // @ts-expect-error
        component.isExpanded.set(true);
        fixture.detectChanges();

        const buttons = fixture.nativeElement.querySelectorAll('.iris-toggle-btn');
        // When expanded: show-less button is visible (show-more is not shown because !shouldClamp)
        expect(buttons.length).toBeGreaterThan(0);
    });

    it('collapse() should set isExpanded to false', () => {
        // @ts-expect-error
        component.isExpanded.set(true);
        component.collapse();
        expect(component['isExpanded']()).toBe(false);
    });

    it('should render source chips when sources are present', () => {
        // @ts-expect-error
        component.phase.set('answering');
        // @ts-expect-error — accessing protected signal for testing
        component.irisResult.set({ answer: 'Some answer', sources: SOURCES.slice(0, 2) });
        fixture.detectChanges();

        const chips = fixture.nativeElement.querySelectorAll('a.iris-chip');
        expect(chips.length).toBe(2);
    });

    it('should show the "+N more" button when there are more than 2 sources', () => {
        // @ts-expect-error
        component.phase.set('answering');
        // @ts-expect-error — accessing protected signal for testing
        component.irisResult.set({ answer: 'Some answer', sources: SOURCES });
        fixture.detectChanges();

        const moreBtn = fixture.nativeElement.querySelector('.iris-more-btn');
        expect(moreBtn).toBeTruthy();
    });

    it('should expand all sources when the "+N more" button is clicked', () => {
        // @ts-expect-error
        component.phase.set('answering');
        // @ts-expect-error — accessing protected signal for testing
        component.irisResult.set({ answer: 'Some answer', sources: SOURCES });
        fixture.detectChanges();

        const moreBtn = fixture.nativeElement.querySelector('.iris-more-btn');
        moreBtn.click();
        fixture.detectChanges();

        const chips = fixture.nativeElement.querySelectorAll('a.iris-chip');
        expect(chips.length).toBe(SOURCES.length);
    });

    it('should show the collapse button when all sources are expanded', () => {
        // @ts-expect-error
        component.phase.set('answering');
        // @ts-expect-error — accessing protected signal for testing
        component.irisResult.set({ answer: 'Some answer', sources: SOURCES });
        fixture.detectChanges(); // effect runs, resets moreOpen=false
        // @ts-expect-error
        component.moreOpen.set(true); // set AFTER effect ran — does not re-trigger it
        fixture.detectChanges();

        const collapseBtn = fixture.nativeElement.querySelector('.iris-collapse-btn');
        expect(collapseBtn).toBeTruthy();
    });

    it('should collapse sources when the collapse button is clicked', () => {
        // @ts-expect-error
        component.phase.set('answering');
        // @ts-expect-error — accessing protected signal for testing
        component.irisResult.set({ answer: 'Some answer', sources: SOURCES });
        fixture.detectChanges(); // effect runs, resets moreOpen=false
        // @ts-expect-error
        component.moreOpen.set(true);
        fixture.detectChanges();

        const collapseBtn = fixture.nativeElement.querySelector('.iris-collapse-btn');
        collapseBtn.click();
        fixture.detectChanges();

        expect(component['moreOpen']()).toBe(false);
    });

    it('should reset isExpanded, isOverflowing, and moreOpen when irisResult changes', () => {
        // @ts-expect-error
        component.phase.set('answering');
        // @ts-expect-error — accessing protected signal for testing
        component.irisResult.set({ answer: 'First answer', sources: [] });
        // @ts-expect-error
        component.isExpanded.set(true);
        // @ts-expect-error
        component.moreOpen.set(true);
        fixture.detectChanges();

        // Trigger effect by setting a new result
        // @ts-expect-error
        component.phase.set('answering');
        // @ts-expect-error — accessing protected signal for testing
        component.irisResult.set({ answer: 'Second answer', sources: [] });
        fixture.detectChanges();

        expect(component['isExpanded']()).toBe(false);
        expect(component['moreOpen']()).toBe(false);
    });

    describe('ask() pipeline integration', () => {
        it('should call irisSearchAnswerService.ask() after debounce when query is non-empty', () => {
            fixture.componentRef.setInput('searchQuery', 'angular signals');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            expect(mockAsk).toHaveBeenCalledWith('angular signals', 5, [], []);
        });

        it('passes the active course filter to ask() and re-asks when it changes', () => {
            fixture.componentRef.setInput('searchQuery', 'angular signals');
            fixture.componentRef.setInput('courseIds', [14]);
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();
            expect(mockAsk).toHaveBeenCalledWith('angular signals', 5, [14], []);

            fixture.componentRef.setInput('courseIds', [16]);
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();
            expect(mockAsk).toHaveBeenCalledWith('angular signals', 5, [16], []);
        });

        it('should NOT call irisSearchAnswerService.ask() for an empty query', () => {
            mockAsk.mockClear();
            fixture.componentRef.setInput('searchQuery', '   ');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            expect(mockAsk).not.toHaveBeenCalled();
        });

        it('should NOT call ask() for a query in the quite-short band (3 chars)', () => {
            mockAsk.mockClear();
            fixture.componentRef.setInput('searchQuery', 'dee');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            expect(mockAsk).not.toHaveBeenCalled();
        });

        it('should NOT call ask() for a 5-char query but SHOULD for a 6-char query', () => {
            mockAsk.mockClear();
            fixture.componentRef.setInput('searchQuery', 'abcde'); // 5 chars, still quite short
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();
            expect(mockAsk).not.toHaveBeenCalled();

            fixture.componentRef.setInput('searchQuery', 'abcdef'); // 6 chars, past the quite-short band
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();
            expect(mockAsk).toHaveBeenCalledWith('abcdef', 5, [], []);
        });

        it('should NOT call ask() before the debounce period has elapsed', () => {
            mockAsk.mockClear();
            fixture.componentRef.setInput('searchQuery', 'signals');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300 - 1);
            fixture.detectChanges();

            expect(mockAsk).not.toHaveBeenCalled();
        });

        it('should enter the thinking phase when a thinking update is received', () => {
            fixture.componentRef.setInput('searchQuery', 'what are signals?');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            askSubject.next({ runId: 'run-1', isThinking: true });
            fixture.detectChanges();

            expect(component['phase']()).toBe('thinking');
        });

        it('should read the stage from a thinking update', () => {
            fixture.componentRef.setInput('searchQuery', 'what are signals?');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            askSubject.next({ runId: 'run-1', isThinking: true, stage: 'searching' });
            fixture.detectChanges();

            expect(component['stage']()).toBe('searching');
            expect(component['stageStatusKey']()).toBe('global.search.irisAnswerStageSearching');
        });

        it('should read the course names from a found-stage update', () => {
            fixture.componentRef.setInput('searchQuery', 'what are signals?');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            askSubject.next({ runId: 'run-1', isThinking: true, stage: 'found', stageSources: ['Advanced Algorithms'] });
            fixture.detectChanges();

            expect(component['stageSources']()).toEqual(['Advanced Algorithms']);
            expect(component['stageStatusKey']()).toBe('global.search.irisAnswerStageFoundSources');
        });

        it('should show ranking as its own stage', () => {
            fixture.componentRef.setInput('searchQuery', 'what are signals?');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            askSubject.next({ runId: 'run-1', isThinking: true, stage: 'ranking' });
            fixture.detectChanges();

            expect(component['stage']()).toBe('ranking');
            expect(component['stageStatusKey']()).toBe('global.search.irisAnswerStageRanking');
        });

        it('should hold a stage on screen for its minimum time before a later one replaces it', () => {
            // "found" and "generating" can arrive back to back with nothing but a synchronous
            // check between them server-side — the second must not instantly erase the first.
            fixture.componentRef.setInput('searchQuery', 'what are signals?');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            askSubject.next({ runId: 'run-1', isThinking: true, stage: 'found', stageSources: ['Advanced Algorithms'] });
            fixture.detectChanges();
            expect(component['stage']()).toBe('found');

            askSubject.next({ runId: 'run-1', isThinking: true, stage: 'generating' });
            fixture.detectChanges();
            expect(component['stage']()).toBe('found');

            vi.advanceTimersByTime(700);
            fixture.detectChanges();
            expect(component['stage']()).toBe('generating');
        });

        it('should still show every queued stage even when several arrive faster than the minimum hold time', () => {
            fixture.componentRef.setInput('searchQuery', 'what are signals?');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            askSubject.next({ runId: 'run-1', isThinking: true, stage: 'searching' });
            askSubject.next({ runId: 'run-1', isThinking: true, stage: 'ranking' });
            askSubject.next({ runId: 'run-1', isThinking: true, stage: 'found', stageSources: ['Advanced Algorithms'] });
            askSubject.next({ runId: 'run-1', isThinking: true, stage: 'generating' });
            fixture.detectChanges();
            expect(component['stage']()).toBe('searching');

            vi.advanceTimersByTime(700);
            fixture.detectChanges();
            expect(component['stage']()).toBe('ranking');

            vi.advanceTimersByTime(700);
            fixture.detectChanges();
            expect(component['stage']()).toBe('found');

            vi.advanceTimersByTime(700);
            fixture.detectChanges();
            expect(component['stage']()).toBe('generating');
        });

        it('should drop a queued or held stage once real streamed text arrives', () => {
            fixture.componentRef.setInput('searchQuery', 'what are signals?');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            askSubject.next({ runId: 'run-1', isThinking: true, stage: 'found', stageSources: ['Advanced Algorithms'] });
            askSubject.next({ runId: 'run-1', isThinking: true, stage: 'generating' });
            fixture.detectChanges();

            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'Signals are', partialSeq: 1 });
            fixture.detectChanges();

            expect(component['phase']()).toBe('answering');
            // The still-queued "generating" stage must not fire later and do anything observable.
            vi.advanceTimersByTime(700);
            fixture.detectChanges();
            expect(component['phase']()).toBe('answering');
        });

        it('should set irisResult with the answer when the final update is received', () => {
            fixture.componentRef.setInput('searchQuery', 'what are signals?');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            askSubject.next({ runId: 'run-1', isThinking: false, answer: 'Signals are reactive.', sources: [] });
            fixture.detectChanges();

            expect(component['phase']()).not.toBe('thinking');
            expect(component['irisResult']()).toEqual({ answer: 'Signals are reactive.', sources: [], entitySources: [] });
        });

        it('should set irisResult to undefined if the final update has no answer', () => {
            fixture.componentRef.setInput('searchQuery', 'navigate somewhere');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            askSubject.next({ runId: 'run-1', isThinking: false });
            fixture.detectChanges();

            expect(component['irisResult']()).toBeUndefined();
        });

        it('should reset the result and the phase immediately when a new query is emitted', () => {
            // First query resolves
            fixture.componentRef.setInput('searchQuery', 'query one');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            askSubject.next({ runId: 'run-1', isThinking: false, answer: 'First answer', sources: [] });
            fixture.detectChanges();
            expect(component['irisResult']()?.answer).toBe('First answer');

            // New query — outer switchMap runs synchronously on emission, cancelling the timer before it fires
            fixture.componentRef.setInput('searchQuery', 'query two');
            fixture.detectChanges();

            expect(component['irisResult']()).toBeUndefined();
            expect(component['phase']()).not.toBe('thinking');
        });

        it('should ignore a final update whose runId does not match the thinking update', () => {
            fixture.componentRef.setInput('searchQuery', 'what are signals?');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            askSubject.next({ runId: 'run-1', isThinking: true });
            fixture.detectChanges();

            askSubject.next({ runId: 'run-stale', isThinking: false, answer: 'Stale answer', sources: [] });
            fixture.detectChanges();

            expect(component['irisResult']()).toBeUndefined();
            expect(component['phase']()).toBe('thinking');
        });

        it('should accept a final update whose runId matches the thinking update', () => {
            fixture.componentRef.setInput('searchQuery', 'what are signals?');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            askSubject.next({ runId: 'run-1', isThinking: true });
            fixture.detectChanges();
            askSubject.next({ runId: 'run-1', isThinking: false, answer: 'Valid answer', sources: [] });
            fixture.detectChanges();

            expect(component['irisResult']()?.answer).toBe('Valid answer');
        });
    });

    describe('streamed partial answers', () => {
        function startQuery() {
            fixture.componentRef.setInput('searchQuery', 'what are signals?');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();
        }

        it('renders the streamed draft instead of the thinking bubble', () => {
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: true });
            fixture.detectChanges();
            expect(component['phase']()).toBe('thinking');

            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'Signals are', partialSeq: 1 });
            fixture.detectChanges();
            expect(component['phase']()).not.toBe('thinking');
            expect(component['irisResult']()).toEqual({ answer: 'Signals are', sources: [] });

            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'Signals are reactive primitives.', partialSeq: 2 });
            fixture.detectChanges();
            expect(component['irisResult']()?.answer).toBe('Signals are reactive primitives.');
        });

        it('ignores an out-of-order partial snapshot', () => {
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'Longer draft text.', partialSeq: 3 });
            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'Old', partialSeq: 2 });
            fixture.detectChanges();
            expect(component['irisResult']()?.answer).toBe('Longer draft text.');
        });

        it('accepts a clearDraft signal when its partialSeq is newer, ordering by sequence rather than length', () => {
            // A provider retry mid-stream restarts the draft from empty with a HIGHER partialSeq
            // (Iris's PartialResultSender sends an empty partial specifically to clear a stale one,
            // forwarded by the server as clearDraft=true since an empty partialResult does not survive
            // its NON_EMPTY serialization); a length comparison would wrongly reject this as stale.
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'A fairly long draft before the retry.', partialSeq: 3 });
            fixture.detectChanges();
            expect(component['irisResult']()?.answer).toBe('A fairly long draft before the retry.');

            askSubject.next({ runId: 'run-1', isThinking: true, clearDraft: true, partialSeq: 4 });
            fixture.detectChanges();
            expect(component['irisResult']()?.answer).toBe('');

            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'Restarted', partialSeq: 5 });
            fixture.detectChanges();
            expect(component['irisResult']()?.answer).toBe('Restarted');
        });

        it('falls back to length ordering when an older Iris omits partialSeq', () => {
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'Signals are' });
            fixture.detectChanges();
            expect(component['irisResult']()?.answer).toBe('Signals are');

            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'Short' });
            fixture.detectChanges();
            expect(component['irisResult']()?.answer).toBe('Signals are');

            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'Signals are reactive.' });
            fixture.detectChanges();
            expect(component['irisResult']()?.answer).toBe('Signals are reactive.');
        });

        it('does not re-show the thinking bubble after a draft started', () => {
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'Draft', partialSeq: 1 });
            askSubject.next({ runId: 'run-1', isThinking: true });
            fixture.detectChanges();
            expect(component['phase']()).not.toBe('thinking');
        });

        it('renders draft markers as citation chips before any sources exist', () => {
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'A claim.[2]', partialSeq: 1 });
            revealEverything();
            expect(component['citationView']().html).toContain('<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>');
        });

        it('replaces the draft with the terminal answer and resets the partial state', () => {
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'Draft.[1]', partialSeq: 5 });
            askSubject.next({ runId: 'run-1', isThinking: false, answer: 'Final answer.[1]', sources: SOURCES });
            revealEverything();
            expect(component['irisResult']()).toEqual({ answer: 'Final answer.[1]', sources: SOURCES, entitySources: [] });
            expect(component['isSettled']()).toBe(true);
        });

        it('stops rescheduling the reveal instead of looping forever on an unclosed marker with no more text coming', () => {
            // A citation marker left open at the very end of what's been streamed blocks reveal
            // progress until the stream completes or a later update closes it — mid-stream (not yet
            // complete), rescheduling regardless of that would loop forever with zero progress each
            // cycle if nothing else ever arrives to unblock it.
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'See the source[1', partialSeq: 1 });
            fixture.detectChanges();

            vi.advanceTimersByTime(10000);
            fixture.detectChanges();

            expect(component['revealTimeout']).toBeUndefined();
        });
    });

    describe('inline citations', () => {
        const MARKED_ANSWER = 'The quiz is worth 4 points.[1] It covers RNNs.[2][3]';

        beforeEach(() => {
            // @ts-expect-error — accessing protected signal for testing
            component.phase.set('answering');
            // @ts-expect-error — accessing protected signal for testing
            component.irisResult.set({ answer: MARKED_ANSWER, sources: SOURCES });
            fixture.detectChanges();
        });

        it('converts each marker into its own citation chip', () => {
            // @ts-expect-error — protected computed
            const view = component.citationView();
            expect(view.html).toContain('<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup>');
            expect(view.html).toContain('<sup class="iris-cite" data-n="2" role="link" tabindex="0">2</sup>');
            expect(view.html).toContain('<sup class="iris-cite" data-n="3" role="link" tabindex="0">3</sup>');
            expect([...view.citedNumbers].sort()).toEqual([1, 2, 3]);
        });

        it('numbers the visible source chips when the answer carries citations', () => {
            const numbers = fixture.nativeElement.querySelectorAll('[data-testid="iris-chip-number"]');
            expect(numbers.length).toBe(2); // INITIAL_VISIBLE_SOURCE_COUNT chips are visible
            expect(numbers[0].textContent.trim()).toBe('1');
            expect(numbers[1].textContent.trim()).toBe('2');
        });

        it('does not number the chips for a markerless answer', () => {
            // @ts-expect-error
            component.phase.set('answering');
            // @ts-expect-error — accessing protected signal for testing
            component.irisResult.set({ answer: 'Plain answer.', sources: SOURCES });
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelectorAll('[data-testid="iris-chip-number"]').length).toBe(0);
        });

        it('keeps a source highlighted after the pointer leaves its chip', () => {
            // The highlight has to survive the trip into the answer: releasing it on the chip's own
            // mouseleave would make reading what a source supports need a click, and a click opens it.
            const chip = fixture.nativeElement.querySelector('.iris-chip');
            chip.dispatchEvent(new Event('mouseenter'));
            fixture.detectChanges();
            expect(chip.classList).toContain('iris-chip-lit');

            chip.dispatchEvent(new Event('mouseleave'));
            fixture.detectChanges();
            expect(chip.classList).toContain('iris-chip-lit');
        });

        it('clears the highlight when the pointer leaves the card', () => {
            const chip = fixture.nativeElement.querySelector('.iris-chip');
            chip.dispatchEvent(new Event('mouseenter'));
            fixture.detectChanges();
            expect(chip.classList).toContain('iris-chip-lit');

            fixture.nativeElement.querySelector('.iris-inline-answer').dispatchEvent(new Event('mouseleave'));
            fixture.detectChanges();
            expect(chip.classList).not.toContain('iris-chip-lit');
        });

        /** The markdown directive is mocked, so the citation chips are injected into the answer body directly. */
        function injectRenderedCitation(dataN: string): HTMLElement {
            const body = fixture.nativeElement.querySelector('.iris-answer-text');
            body.innerHTML = `<p>Claim<sup class="iris-cite" data-n="${dataN}">${dataN}</sup></p>`;
            return body.querySelector('.iris-cite');
        }

        it('shows the source popover while an inline citation is hovered', () => {
            const sup = injectRenderedCitation('1');
            sup.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
            fixture.detectChanges();
            const popover = fixture.nativeElement.querySelector('[data-testid="iris-citation-popover"]');
            expect(popover).toBeTruthy();
            expect(popover.textContent).toContain('Unit 1');

            sup.dispatchEvent(new MouseEvent('mouseout', { bubbles: true }));
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('[data-testid="iris-citation-popover"]')).toBeNull();
        });

        it('highlights the passage and its citation chip while hovered', () => {
            const sup = injectRenderedCitation('1');
            sup.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
            fixture.detectChanges();
            expect(sup.classList).toContain('iris-cite-lit');
            expect(sup.closest('p')!.classList).toContain('iris-attr-lit');
        });

        it('opens the cited source on click, exactly like its chip', () => {
            const router = TestBed.inject(Router);
            const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
            const sup = injectRenderedCitation('3');
            sup.dispatchEvent(new MouseEvent('click', { bubbles: true }));
            fixture.detectChanges();
            expect(navigateSpy).toHaveBeenCalledWith(['/u/3'], { queryParams: SOURCES[2].lectureUnit.queryParams });
        });

        it('ignores a click on a draft citation before sources arrived', () => {
            const router = TestBed.inject(Router);
            const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
            // @ts-expect-error — protected signal
            component.phase.set('answering');
            // @ts-expect-error — accessing protected signal for testing
            component.irisResult.set({ answer: 'Draft.[1]', sources: [] });
            fixture.detectChanges();
            const sup = injectRenderedCitation('1');
            sup.dispatchEvent(new MouseEvent('click', { bubbles: true }));
            expect(navigateSpy).not.toHaveBeenCalled();
        });
    });

    describe('entity sources', () => {
        beforeEach(() => {
            // @ts-expect-error — accessing protected signal for testing
            component.phase.set('answering');
            // @ts-expect-error — accessing protected signal for testing
            component.irisResult.set({ answer: 'Worth 10 points.[4] From slides.[1]', sources: SOURCES, entitySources: ENTITY_SOURCES });
            fixture.detectChanges();
        });

        it('renders entity chips numbered after the lecture sources, with the palette icon', () => {
            const chips = fixture.nativeElement.querySelectorAll('[data-testid="iris-entity-chip"]');
            expect(chips.length).toBe(1);
            expect(chips[0].textContent).toContain('W03E03 Flyweight Pattern');
            expect(chips[0].textContent).toContain('Patterns in Software Engineering');
            expect(chips[0].querySelector('[data-testid="iris-chip-number"]').textContent.trim()).toBe('4');
            expect(chips[0].querySelector('fa-icon')).toBeTruthy();
        });

        it('renders entity chips before the more-sources expander', () => {
            const row = fixture.nativeElement.querySelector('.iris-chips');
            const children = [...row.children];
            const entityIndex = children.findIndex((el: Element) => el.getAttribute('data-testid') === 'iris-entity-chip');
            const moreIndex = children.findIndex((el: Element) => el.classList.contains('iris-more-btn'));
            expect(entityIndex).toBeGreaterThan(-1);
            expect(moreIndex).toBeGreaterThan(entityIndex);
        });

        it('uses the same icon mapping as the palette for entity chips', () => {
            // @ts-expect-error — protected method
            expect(component.entityIcon(ENTITY_SOURCES[0])).toBe(faKeyboard);
        });

        it('counts entity sources when validating citation markers', () => {
            // marker [4] indexes the entity source (3 lecture sources + 1 entity)
            // @ts-expect-error — protected computed
            const view = component.citationView();
            expect(view.html).toContain('<sup class="iris-cite" data-n="4" role="link" tabindex="0">4</sup>');
        });

        it('opens the entity link when its chip is clicked', () => {
            const router = TestBed.inject(Router);
            const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
            fixture.nativeElement.querySelector('[data-testid="iris-entity-chip"]').click();
            expect(navigateSpy).toHaveBeenCalledWith('/courses/9/exercises/42');
        });

        it('opens the entity link when its inline citation is clicked', () => {
            const router = TestBed.inject(Router);
            const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
            const body = fixture.nativeElement.querySelector('.iris-answer-text');
            body.innerHTML = '<p>Worth 10 points.<sup class="iris-cite" data-n="4">4</sup></p>';
            body.querySelector('.iris-cite').dispatchEvent(new MouseEvent('click', { bubbles: true }));
            expect(navigateSpy).toHaveBeenCalledWith('/courses/9/exercises/42');
        });

        it('shows the entity popover for a hovered entity citation', () => {
            const body = fixture.nativeElement.querySelector('.iris-answer-text');
            body.innerHTML = '<p>Worth 10 points.<sup class="iris-cite" data-n="4">4</sup></p>';
            body.querySelector('.iris-cite').dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
            fixture.detectChanges();
            const popover = fixture.nativeElement.querySelector('[data-testid="iris-citation-popover"]');
            expect(popover.textContent).toContain('W03E03 Flyweight Pattern');
            expect(popover.textContent).toContain('Patterns in Software Engineering');
        });

        it('carries entity sources from the terminal update into the result', () => {
            fixture.componentRef.setInput('searchQuery', 'flyweight points');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();
            askSubject.next({ runId: 'run-1', isThinking: false, answer: 'A.[1]', sources: SOURCES, entitySources: ENTITY_SOURCES });
            fixture.detectChanges();
            // @ts-expect-error — protected signal
            expect(component.irisResult()?.entitySources).toEqual(ENTITY_SOURCES);
        });
    });

    describe('citation source types (entity cited before any lecture source)', () => {
        const LECTURE_SOURCES = [SOURCES[0]];

        beforeEach(() => {
            // @ts-expect-error — accessing protected signal for testing
            component.phase.set('answering');
            // @ts-expect-error — accessing protected signal for testing
            component.irisResult.set({
                answer: 'About the course.[1] About the slide.[2]',
                sources: LECTURE_SOURCES,
                entitySources: ENTITY_SOURCES,
                citationSourceTypes: ['entity', 'lecture'],
            });
            fixture.detectChanges();
        });

        it('numbers the entity chip [1] and the lecture chip [2] instead of the old fixed lecture-then-entity block order', () => {
            const entityChip = fixture.nativeElement.querySelector('[data-testid="iris-entity-chip"]');
            const lectureChip = fixture.nativeElement.querySelector('.iris-chip:not([data-testid="iris-entity-chip"])');
            expect(entityChip.querySelector('[data-testid="iris-chip-number"]').textContent.trim()).toBe('1');
            expect(lectureChip.querySelector('[data-testid="iris-chip-number"]').textContent.trim()).toBe('2');
        });

        it('opens the entity source when its inline [1] citation is clicked, not the lecture source', () => {
            const router = TestBed.inject(Router);
            const navigateByUrlSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
            const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
            const body = fixture.nativeElement.querySelector('.iris-answer-text');
            body.innerHTML = '<p>About the course.<sup class="iris-cite" data-n="1">1</sup></p>';
            body.querySelector('.iris-cite').dispatchEvent(new MouseEvent('click', { bubbles: true }));
            expect(navigateByUrlSpy).toHaveBeenCalledWith('/courses/9/exercises/42');
            expect(navigateSpy).not.toHaveBeenCalled();
        });

        it('opens the lecture source when its inline [2] citation is clicked, not the entity source', () => {
            const router = TestBed.inject(Router);
            const navigateByUrlSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
            const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
            const body = fixture.nativeElement.querySelector('.iris-answer-text');
            body.innerHTML = '<p>About the slide.<sup class="iris-cite" data-n="2">2</sup></p>';
            body.querySelector('.iris-cite').dispatchEvent(new MouseEvent('click', { bubbles: true }));
            expect(navigateSpy).toHaveBeenCalledWith(['/u/1'], { queryParams: SOURCES[0].lectureUnit.queryParams });
            expect(navigateByUrlSpy).not.toHaveBeenCalled();
        });

        it('carries citationSourceTypes from the terminal update into the result', () => {
            fixture.componentRef.setInput('searchQuery', 'course versus slide');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();
            askSubject.next({
                runId: 'run-1',
                isThinking: false,
                answer: 'About the course.[1] About the slide.[2]',
                sources: LECTURE_SOURCES,
                entitySources: ENTITY_SOURCES,
                citationSourceTypes: ['entity', 'lecture'],
            });
            fixture.detectChanges();
            // @ts-expect-error — protected signal
            expect(component.irisResult()?.citationSourceTypes).toEqual(['entity', 'lecture']);
        });
    });

    describe('SOURCE_ICONS', () => {
        it('should map lecture_unit_slide to faFilePdf', () => {
            expect(component['SOURCE_ICONS']['lecture_unit_slide']).toBe(faFilePdf);
        });

        it('should map lecture_unit_slide_video to faFileVideo', () => {
            expect(component['SOURCE_ICONS']['lecture_unit_slide_video']).toBe(faFileVideo);
        });

        it('should map lecture_unit_video to faVideo', () => {
            expect(component['SOURCE_ICONS']['lecture_unit_video']).toBe(faVideo);
        });

        it('should fall back to faFile for an unknown source type', () => {
            expect(component['SOURCE_ICONS']['unknown_type'] ?? component['faFile']).toBe(faFile);
        });
    });

    describe('the card while Iris is working', () => {
        function startQuery(): void {
            fixture.componentRef.setInput('searchQuery', 'what are signals?');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();
        }

        it('stays a slim strip until there is something to show', () => {
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: true });
            fixture.detectChanges();

            expect(component['isOpen']()).toBe(false);
            expect(fixture.nativeElement.querySelector('.iris-inline-answer').classList).not.toContain('is-open');
            expect(fixture.nativeElement.querySelector('[data-testid="iris-strip-status"]')).toBeTruthy();
        });

        it('opens the card as soon as the first draft arrives', () => {
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: true });
            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'Signals are', partialSeq: 1 });
            fixture.detectChanges();

            expect(component['isOpen']()).toBe(true);
            expect(fixture.nativeElement.querySelector('.iris-inline-answer').classList).toContain('is-open');
        });

        it('reveals a streamed draft progressively rather than all at once', () => {
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'Signals are reactive primitives in Angular.', partialSeq: 1 });
            fixture.detectChanges();
            expect(component['displayedAnswer']()).toBe('');

            vi.advanceTimersByTime(80);
            const partway = component['displayedAnswer']();
            expect(partway.length).toBeGreaterThan(0);
            expect(partway.length).toBeLessThan('Signals are reactive primitives in Angular.'.length);

            revealEverything();
            expect(component['displayedAnswer']()).toBe('Signals are reactive primitives in Angular.');
        });

        it('never reveals half of a citation marker', () => {
            startQuery();
            // The draft breaks mid-marker, which is what arriving in clumps looks like.
            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'A claim [1', partialSeq: 1 });
            vi.advanceTimersByTime(5000);
            fixture.detectChanges();

            expect(component['displayedAnswer']()).not.toContain('[1');
        });

        it('does not inject the fade-tail span inside a complete inline code span, which would corrupt the markdown', () => {
            // Reproduces revealing the second word of `foo bar`: the reveal boundary lands between the
            // words, strictly inside the backtick pair. Splicing the span there would put an HTML tag
            // inside markdown-it's inline code span, which renders it as literal text instead of a chip.
            startQuery();
            // @ts-expect-error — accessing protected signal for testing
            component.phase.set('answering');
            // @ts-expect-error — accessing private signal for testing
            component.progressiveReveal.set(true);
            const answer = 'See `foo bar` for details.';
            // @ts-expect-error — accessing protected signal for testing
            component.irisResult.set({ answer, sources: [] });
            // @ts-expect-error — accessing private signal for testing
            component.revealStart.set(answer.indexOf('bar'));
            // @ts-expect-error — accessing private signal for testing
            component.revealedLength.set(answer.length);
            fixture.detectChanges();

            // @ts-expect-error — accessing protected computed for testing
            const html = component.citationView().html;
            expect(html).not.toContain('iris-answer-tail');
        });

        it('does not inject the fade-tail span inside a double-backtick-delimited code span either', () => {
            // A double-backtick run ("``") is a single CommonMark delimiter, not two single backticks
            // that cancel each other out — this is what lets the span's own content safely contain a
            // literal single backtick. Pairing individual backtick characters would miss this entirely.
            startQuery();
            // @ts-expect-error — accessing protected signal for testing
            component.phase.set('answering');
            // @ts-expect-error — accessing private signal for testing
            component.progressiveReveal.set(true);
            const answer = 'See ``foo bar`` for details.';
            // @ts-expect-error — accessing protected signal for testing
            component.irisResult.set({ answer, sources: [] });
            // @ts-expect-error — accessing private signal for testing
            component.revealStart.set(answer.indexOf('bar'));
            // @ts-expect-error — accessing private signal for testing
            component.revealedLength.set(answer.length);
            fixture.detectChanges();

            // @ts-expect-error — accessing protected computed for testing
            const html = component.citationView().html;
            expect(html).not.toContain('iris-answer-tail');
        });

        it('does not inject the fade-tail span inside a still-streaming backtick fence with no closer yet', () => {
            // The closing ``` has not arrived, but the block is already "inside code" as far as
            // Markdown is concerned from the moment the opening fence line appeared — the closer
            // does not need to exist yet for that to be true, unlike an inline span.
            startQuery();
            // @ts-expect-error — accessing protected signal for testing
            component.phase.set('answering');
            // @ts-expect-error — accessing private signal for testing
            component.progressiveReveal.set(true);
            const answer = '```js\nconst value = foo bar';
            // @ts-expect-error — accessing protected signal for testing
            component.irisResult.set({ answer, sources: [] });
            // @ts-expect-error — accessing private signal for testing
            component.revealStart.set(answer.indexOf('bar'));
            // @ts-expect-error — accessing private signal for testing
            component.revealedLength.set(answer.length);
            fixture.detectChanges();

            // @ts-expect-error — accessing protected computed for testing
            const html = component.citationView().html;
            expect(html).not.toContain('iris-answer-tail');
        });

        it('does not inject the fade-tail span inside a tilde fence either', () => {
            // CommonMark's other fenced-code-block delimiter; the inline-span guard only ever looks
            // at backticks, so a tilde fence needs its own check.
            startQuery();
            // @ts-expect-error — accessing protected signal for testing
            component.phase.set('answering');
            // @ts-expect-error — accessing private signal for testing
            component.progressiveReveal.set(true);
            const answer = '~~~js\nconst value = foo bar\n~~~';
            // @ts-expect-error — accessing protected signal for testing
            component.irisResult.set({ answer, sources: [] });
            // @ts-expect-error — accessing private signal for testing
            component.revealStart.set(answer.indexOf('bar'));
            // @ts-expect-error — accessing private signal for testing
            component.revealedLength.set(answer.length);
            fixture.detectChanges();

            // @ts-expect-error — accessing protected computed for testing
            const html = component.citationView().html;
            expect(html).not.toContain('iris-answer-tail');
        });

        it('does not treat a fence-like content line inside the block as its closer', () => {
            // "```not-a-closer" starts with a same-length run but has non-whitespace after it, so
            // CommonMark does NOT treat it as the closing fence — everything after it, up to the
            // REAL closer on its own line, is still fenced content.
            startQuery();
            // @ts-expect-error — accessing protected signal for testing
            component.phase.set('answering');
            // @ts-expect-error — accessing private signal for testing
            component.progressiveReveal.set(true);
            const answer = '```js\nfoo\n```not-a-closer\nbar\n```';
            // @ts-expect-error — accessing protected signal for testing
            component.irisResult.set({ answer, sources: [] });
            // @ts-expect-error — accessing private signal for testing
            component.revealStart.set(answer.indexOf('bar'));
            // @ts-expect-error — accessing private signal for testing
            component.revealedLength.set(answer.length);
            fixture.detectChanges();

            // @ts-expect-error — accessing protected computed for testing
            const html = component.citationView().html;
            expect(html).not.toContain('iris-answer-tail');
        });

        it('does not inject the fade-tail span inside a streamed inline math expression', () => {
            // A reveal boundary landing inside $x + y$ would put the tag inside the formula
            // markdown-it/KaTeX parses, corrupting it the same way a code span would.
            startQuery();
            // @ts-expect-error — accessing protected signal for testing
            component.phase.set('answering');
            // @ts-expect-error — accessing private signal for testing
            component.progressiveReveal.set(true);
            const answer = 'The result is $x + y$ as shown.';
            // @ts-expect-error — accessing protected signal for testing
            component.irisResult.set({ answer, sources: [] });
            // @ts-expect-error — accessing private signal for testing
            component.revealStart.set(answer.indexOf('y'));
            // @ts-expect-error — accessing private signal for testing
            component.revealedLength.set(answer.length);
            fixture.detectChanges();

            // @ts-expect-error — accessing protected computed for testing
            const html = component.citationView().html;
            expect(html).not.toContain('iris-answer-tail');
        });

        it('does not inject the fade-tail span inside a streamed display math block with no closer yet', () => {
            // Mirrors the still-streaming fence case: the closing $$ has not arrived, but the reveal
            // boundary already sits inside the formula as far as KaTeX will eventually parse it. Kept
            // on one line (no closer) so the tail itself carries no newline of its own to test against —
            // otherwise the pre-existing newline guard alone would already block the animation, and the
            // math check would never actually run.
            startQuery();
            // @ts-expect-error — accessing protected signal for testing
            component.phase.set('answering');
            // @ts-expect-error — accessing private signal for testing
            component.progressiveReveal.set(true);
            const answer = 'Consider $$x + y = z';
            // @ts-expect-error — accessing protected signal for testing
            component.irisResult.set({ answer, sources: [] });
            // @ts-expect-error — accessing private signal for testing
            component.revealStart.set(answer.indexOf('z'));
            // @ts-expect-error — accessing private signal for testing
            component.revealedLength.set(answer.length);
            fixture.detectChanges();

            // @ts-expect-error — accessing protected computed for testing
            const html = component.citationView().html;
            expect(html).not.toContain('iris-answer-tail');
        });

        it('shows an answer delivered in one piece immediately', () => {
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: false, answer: 'Signals are reactive.', sources: [] });
            fixture.detectChanges();

            // Nothing arrived progressively, so there is nothing to smooth out and nothing to wait for.
            expect(component['displayedAnswer']()).toBe('Signals are reactive.');
            expect(component['isSettled']()).toBe(true);
        });

        it('withholds the show-more toggle until the answer has settled', () => {
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'A long draft that keeps going.', partialSeq: 1 });
            fixture.detectChanges();
            // @ts-expect-error — accessing protected signal for testing
            component.isOverflowing.set(true);
            fixture.detectChanges();

            expect(component['shouldClamp']()).toBe(false);
            expect(fixture.nativeElement.querySelector('.iris-toggle-btn')).toBeNull();
        });
    });

    describe('how the card ends', () => {
        function startQuery(): void {
            fixture.componentRef.setInput('searchQuery', 'what are signals?');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();
        }

        it('says so when nothing is relevant instead of vanishing', () => {
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: true });
            askSubject.next({ runId: 'run-1', isThinking: false });
            fixture.detectChanges();

            expect(component['phase']()).toBe('noAnswer');
            expect(fixture.nativeElement.querySelector('[data-testid="iris-no-answer"]')).toBeTruthy();
        });

        it('folds the no-answer card away once it has been read', () => {
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: true });
            askSubject.next({ runId: 'run-1', isThinking: false });
            fixture.detectChanges();
            expect(component['isDismissed']()).toBe(false);

            vi.advanceTimersByTime(6000);
            fixture.detectChanges();

            expect(component['isDismissed']()).toBe(true);
            expect(fixture.nativeElement.querySelector('.iris-inline-answer').classList).toContain('is-dismissed');
        });

        it('shows nothing at all for a keyword search Iris never attempted to answer', () => {
            // No isThinking:true ever arrives here — that only happens on the TRIGGER_AI path,
            // sent before the pipeline even starts. A terminal update with no answer and no prior
            // thinking update means the server classified this as a plain navigational/keyword
            // query (SKIP_AI) and never asked Iris to judge it at all. Observed live: "lecture 2"
            // showed "Iris looked, but nothing found is relevant enough" — false, since Iris never
            // looked — for every such keyword search.
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: false });
            fixture.detectChanges();

            expect(component['phase']()).toBe('idle');
            expect(fixture.nativeElement.querySelector('[data-testid="iris-no-answer"]')).toBeNull();
            expect(fixture.nativeElement.querySelector('.iris-inline-answer')).toBeNull();
        });

        it('clears a pending reveal timer when the stream fails after a partial already arrived', () => {
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'A draft that was mid-reveal.', partialSeq: 1 });
            fixture.detectChanges();
            expect(component['revealTimeout']).toBeDefined();

            askSubject.error(new Error('pipeline down'));
            fixture.detectChanges();

            expect(component['phase']()).toBe('failed');
            expect(component['revealTimeout']).toBeUndefined();
        });

        it('clears a pending reveal timer when a streamed partial ends with a considered no-answer result', () => {
            // The same partial-then-terminal race as the failure case above: a reveal timer left
            // running here would keep revealing text belonging to a draft the no-answer card is about
            // to replace, and streamComplete never gets set on this path to make it stop on its own.
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'A draft that was mid-reveal.', partialSeq: 1 });
            fixture.detectChanges();
            expect(component['revealTimeout']).toBeDefined();

            askSubject.next({ runId: 'run-1', isThinking: false });
            fixture.detectChanges();

            expect(component['phase']()).toBe('noAnswer');
            expect(component['revealTimeout']).toBeUndefined();
        });

        it('ignores a partial that arrives after the terminal update instead of reopening the finished answer', () => {
            // Iris's own partial sender documents this as a race it cannot fully close on
            // its own side (a POST already in flight when it stops can still land after the
            // terminal one), and the terminal message carries no partialSeq for the client's
            // usual ordering check to catch it — this guard is what actually closes the gap.
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: false, answer: 'Final answer.[1]', sources: SOURCES });
            expect(component['irisResult']()).toEqual({ answer: 'Final answer.[1]', sources: SOURCES, entitySources: [] });
            expect(component['isSettled']()).toBe(true);

            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'stale draft', partialSeq: 1 });
            fixture.detectChanges();

            expect(component['irisResult']()).toEqual({ answer: 'Final answer.[1]', sources: SOURCES, entitySources: [] });
            expect(component['isSettled']()).toBe(true);
        });

        it('offers a retry when Pyris reports a failed run over the WebSocket, not just a transport error', () => {
            // A genuine Pyris-side failure produces the identical isThinking=false, answer=null shape as a
            // successful "nothing relevant" result unless failed=true is checked first — without that check
            // this would wrongly land in the noAnswer phase, with no retry.
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: false, failed: true });
            fixture.detectChanges();

            expect(component['phase']()).toBe('failed');
            expect(fixture.nativeElement.querySelector('[data-testid="iris-answer-retry"]')).toBeTruthy();
        });

        it('offers a retry when the pipeline fails', () => {
            mockAsk.mockReturnValueOnce(throwError(() => new Error('pipeline down')));
            startQuery();

            expect(component['phase']()).toBe('failed');
            expect(fixture.nativeElement.querySelector('[data-testid="iris-answer-retry"]')).toBeTruthy();
        });

        it('asks again when the retry is used', () => {
            mockAsk.mockReturnValueOnce(throwError(() => new Error('pipeline down')));
            startQuery();
            expect(mockAsk).toHaveBeenCalledTimes(1);

            fixture.nativeElement.querySelector('[data-testid="iris-answer-retry"]').click();
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            expect(mockAsk).toHaveBeenCalledTimes(2);
            expect(component['phase']()).not.toBe('failed');
        });
    });
});
