import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockPipe } from 'ng-mocks';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Subject } from 'rxjs';
import { provideRouter } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
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

describe('GlobalSearchIrisAnswerComponent', () => {
    setupTestBed({ zoneless: true });

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
            imports: [GlobalSearchIrisAnswerComponent, MockPipe(ArtemisTranslatePipe), MockPipe(HtmlForMarkdownPipe)],
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
            expect(component['irisResult']()).toEqual({ answer: 'Signals are reactive.', sources: [] });
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

        it('should reset irisResult and irisThinking when a new query is debounced', () => {
            // First query resolves
            fixture.componentRef.setInput('searchQuery', 'query one');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
            fixture.detectChanges();

            askSubject.next({ runId: 'run-1', isThinking: false, answer: 'First answer', sources: [] });
            fixture.detectChanges();
            expect(component['irisResult']()?.answer).toBe('First answer');

            // New query — tap() resets state once the debounce fires
            fixture.componentRef.setInput('searchQuery', 'query two');
            fixture.detectChanges();
            vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300); // tap() runs here
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

    describe('iconFor()', () => {
        it('should return faFilePdf for lecture_unit_slide', () => {
            expect(component['iconFor']('lecture_unit_slide')).toBe(faFilePdf);
        });

        it('should return faFileVideo for lecture_unit_slide_video', () => {
            expect(component['iconFor']('lecture_unit_slide_video')).toBe(faFileVideo);
        });

        it('should return faVideo for lecture_unit_video', () => {
            expect(component['iconFor']('lecture_unit_video')).toBe(faVideo);
        });

        it('should return faFile for an unknown source type', () => {
            expect(component['iconFor']('unknown_type')).toBe(faFile);
        });
    });
});
