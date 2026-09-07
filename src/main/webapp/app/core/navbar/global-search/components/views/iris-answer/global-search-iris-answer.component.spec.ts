import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockDirective, MockPipe } from 'ng-mocks';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject } from 'rxjs';
import { Router, provideRouter } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { faFile, faFilePdf, faFileVideo, faVideo } from '@fortawesome/free-solid-svg-icons';
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
        component.irisThinking.set(true);
        fixture.detectChanges();

        const card = fixture.nativeElement.querySelector('.iris-inline-answer');
        expect(card).toBeTruthy();
    });

    it('should render the thinking wrapper when irisThinking is true', () => {
        // @ts-expect-error
        component.irisThinking.set(true);
        fixture.detectChanges();

        const thinkingWrapper = fixture.nativeElement.querySelector('.iris-thinking-wrapper');
        expect(thinkingWrapper).toBeTruthy();
    });

    it('should not render the thinking wrapper when irisThinking is false', () => {
        // @ts-expect-error
        component.irisResult.set({ answer: 'Some answer', sources: [] });
        fixture.detectChanges();

        const thinkingWrapper = fixture.nativeElement.querySelector('.iris-thinking-wrapper');
        expect(thinkingWrapper).toBeNull();
    });

    it('should render the answer text region when irisResult has an answer', () => {
        // @ts-expect-error
        component.irisResult.set({ answer: 'Signals are reactive primitives.', sources: [] });
        fixture.detectChanges();

        const answerEl = fixture.nativeElement.querySelector('.iris-answer-text');
        expect(answerEl).toBeTruthy();
    });

    it('should apply is-clamped class when shouldClamp is true', () => {
        // @ts-expect-error
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
        component.irisResult.set({ answer: 'Some answer', sources: SOURCES.slice(0, 2) });
        fixture.detectChanges();

        const chips = fixture.nativeElement.querySelectorAll('a.iris-chip');
        expect(chips.length).toBe(2);
    });

    it('should show the "+N more" button when there are more than 2 sources', () => {
        // @ts-expect-error
        component.irisResult.set({ answer: 'Some answer', sources: SOURCES });
        fixture.detectChanges();

        const moreBtn = fixture.nativeElement.querySelector('.iris-more-btn');
        expect(moreBtn).toBeTruthy();
    });

    it('should expand all sources when the "+N more" button is clicked', () => {
        // @ts-expect-error
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
        component.irisResult.set({ answer: 'First answer', sources: [] });
        // @ts-expect-error
        component.isExpanded.set(true);
        // @ts-expect-error
        component.moreOpen.set(true);
        fixture.detectChanges();

        // Trigger effect by setting a new result
        // @ts-expect-error
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

            expect(mockAsk).toHaveBeenCalledWith('angular signals');
        });

        it('should NOT call irisSearchAnswerService.ask() for an empty query', () => {
            mockAsk.mockClear();
            fixture.componentRef.setInput('searchQuery', '   ');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            expect(mockAsk).not.toHaveBeenCalled();
        });

        it('should NOT call ask() before the debounce period has elapsed', () => {
            mockAsk.mockClear();
            fixture.componentRef.setInput('searchQuery', 'signals');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300 - 1);
            fixture.detectChanges();

            expect(mockAsk).not.toHaveBeenCalled();
        });

        it('should set irisThinking to true when a thinking update is received', () => {
            fixture.componentRef.setInput('searchQuery', 'what are signals?');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            askSubject.next({ runId: 'run-1', isThinking: true });
            fixture.detectChanges();

            expect(component['irisThinking']()).toBe(true);
        });

        it('should set irisResult with the answer when the final update is received', () => {
            fixture.componentRef.setInput('searchQuery', 'what are signals?');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            askSubject.next({ runId: 'run-1', isThinking: false, answer: 'Signals are reactive.', sources: [] });
            fixture.detectChanges();

            expect(component['irisThinking']()).toBe(false);
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

        it('should reset irisResult and irisThinking immediately when a new query is emitted', () => {
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
            expect(component['irisThinking']()).toBe(false);
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
            expect(component['irisThinking']()).toBe(true);
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
            expect(component['irisThinking']()).toBe(true);

            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'Signals are', partialSeq: 1 });
            fixture.detectChanges();
            expect(component['irisThinking']()).toBe(false);
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

        it('does not re-show the thinking bubble after a draft started', () => {
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'Draft', partialSeq: 1 });
            askSubject.next({ runId: 'run-1', isThinking: true });
            fixture.detectChanges();
            expect(component['irisThinking']()).toBe(false);
        });

        it('renders draft markers as citation chips before any sources exist', () => {
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'A claim.[2]', partialSeq: 1 });
            fixture.detectChanges();
            expect(component['citationView']().html).toContain('<sup class="iris-cite" data-n="2">2</sup>');
        });

        it('replaces the draft with the terminal answer and resets the partial state', () => {
            startQuery();
            askSubject.next({ runId: 'run-1', isThinking: true, partialResult: 'Draft.[1]', partialSeq: 5 });
            askSubject.next({ runId: 'run-1', isThinking: false, answer: 'Final answer.[1]', sources: SOURCES });
            fixture.detectChanges();
            expect(component['irisResult']()).toEqual({ answer: 'Final answer.[1]', sources: SOURCES, entitySources: [] });
            expect(component['isPartialAnswer']()).toBe(false);
        });
    });

    describe('inline citations', () => {
        const MARKED_ANSWER = 'The quiz is worth 4 points.[1] It covers RNNs.[2][3]';

        beforeEach(() => {
            // @ts-expect-error — accessing protected signal for testing
            component.irisResult.set({ answer: MARKED_ANSWER, sources: SOURCES });
            fixture.detectChanges();
        });

        it('converts marker runs into citation chip HTML', () => {
            // @ts-expect-error — protected computed
            const view = component.citationView();
            expect(view.html).toContain('<sup class="iris-cite" data-n="1">1</sup>');
            expect(view.html).toContain('<sup class="iris-cite" data-n="2 3">2,3</sup>');
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
            component.irisResult.set({ answer: 'Plain answer.', sources: SOURCES });
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelectorAll('[data-testid="iris-chip-number"]').length).toBe(0);
        });

        it('highlights a source chip while it is hovered', () => {
            const chip = fixture.nativeElement.querySelector('.iris-chip');
            chip.dispatchEvent(new Event('mouseenter'));
            fixture.detectChanges();
            expect(chip.classList).toContain('iris-chip-lit');

            chip.dispatchEvent(new Event('mouseleave'));
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
            component.irisResult.set({ answer: 'Worth 10 points.[4] From slides.[1]', sources: SOURCES, entitySources: ENTITY_SOURCES });
            fixture.detectChanges();
        });

        it('renders entity chips numbered after the lecture sources', () => {
            const chips = fixture.nativeElement.querySelectorAll('[data-testid="iris-entity-chip"]');
            expect(chips.length).toBe(1);
            expect(chips[0].textContent).toContain('W03E03 Flyweight Pattern');
            expect(chips[0].textContent).toContain('Patterns in Software Engineering');
            expect(chips[0].querySelector('[data-testid="iris-chip-number"]').textContent.trim()).toBe('4');
        });

        it('counts entity sources when validating citation markers', () => {
            // marker [4] indexes the entity source (3 lecture sources + 1 entity)
            // @ts-expect-error — protected computed
            const view = component.citationView();
            expect(view.html).toContain('<sup class="iris-cite" data-n="4">4</sup>');
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
});
