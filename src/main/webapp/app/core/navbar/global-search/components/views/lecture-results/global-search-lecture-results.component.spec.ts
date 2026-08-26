import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockPipe } from 'ng-mocks';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { GlobalSearchLectureResultsComponent } from './global-search-lecture-results.component';
import { LectureSearchService } from 'app/core/navbar/global-search/services/lecture-search.service';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { LectureDeepLinkService } from 'app/lecture/overview/course-lectures/lecture-deep-link.service';
import { SearchOverlayService } from 'app/core/navbar/global-search/services/search-overlay.service';

const mockResult: LectureSearchResult = {
    course: { id: 1, name: 'Advanced Web Development' },
    lecture: { id: 1, name: 'Angular Basics' },
    lectureUnit: {
        id: 1,
        name: 'Introduction to Signals',
        link: '/courses/1/lectures/1/units/1',
        pageNumber: 3,
        sourceType: 'lecture_unit_slide',
        queryParams: { unit: 1, page: 3 },
    },
    snippet: 'Signals are a reactive primitive in Angular.',
};

const mockResultNoSnippet: LectureSearchResult = {
    course: { id: 2, name: 'Server-Side Development' },
    lecture: { id: 2, name: 'Backend Fundamentals' },
    lectureUnit: { id: 2, name: 'Spring Boot Overview', link: '/courses/2/lectures/2/units/2', pageNumber: 7, sourceType: 'lecture_unit_slide', queryParams: { unit: 2, page: 7 } },
};

const mockSearchService = { search: vi.fn() };

describe('GlobalSearchLectureResultsComponent', () => {
    let component: GlobalSearchLectureResultsComponent;
    let fixture: ComponentFixture<GlobalSearchLectureResultsComponent>;

    beforeEach(() => {
        vi.clearAllMocks();
        // jsdom does not implement scrollIntoView; stub it to avoid errors from the scroll effect
        Element.prototype.scrollIntoView = vi.fn();
        mockSearchService.search.mockReturnValue(new Subject().asObservable());

        TestBed.configureTestingModule({
            imports: [GlobalSearchLectureResultsComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [provideRouter([]), { provide: LectureSearchService, useValue: mockSearchService }, { provide: TranslateService, useClass: MockTranslateService }],
        });

        fixture = TestBed.createComponent(GlobalSearchLectureResultsComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('searchQuery', '');
        fixture.componentRef.setInput('selectedIndex', -1);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('Loading state', () => {
        it('should show 5 skeleton cards when loading', () => {
            (component as any).isLoading.set(true);
            fixture.detectChanges();

            const skeletonCards = fixture.nativeElement.querySelectorAll('.lecture-skeleton-card');
            expect(skeletonCards).toHaveLength(5);
        });

        it('should hide skeleton cards when not loading', () => {
            (component as any).isLoading.set(false);
            fixture.detectChanges();

            const skeletonCards = fixture.nativeElement.querySelectorAll('.lecture-skeleton-card');
            expect(skeletonCards).toHaveLength(0);
        });
    });

    describe('Results rendering', () => {
        beforeEach(() => {
            // A non-empty query is required to reach the results branch in the template
            fixture.componentRef.setInput('searchQuery', 'angular');
        });

        it('should show results when lectureResults is populated', () => {
            (component as any).isLoading.set(false);
            (component as any).lectureResults.set([mockResult]);
            fixture.detectChanges();

            const cards = fixture.nativeElement.querySelectorAll('.lecture-result-card');
            expect(cards).toHaveLength(1);
        });

        it('should display lecture unit name, course path, and page number', () => {
            (component as any).isLoading.set(false);
            (component as any).lectureResults.set([mockResult]);
            fixture.detectChanges();

            const card = fixture.nativeElement.querySelector('.lecture-result-card');
            const title = card.querySelector('.lecture-card-title');
            const unit = card.querySelector('.lecture-card-unit');
            const page = card.querySelector('.lecture-card-page');

            expect(title.textContent.trim()).toBe('Introduction to Signals');
            expect(unit.textContent).toContain('Advanced Web Development');
            expect(unit.textContent).toContain('Angular Basics');
            expect(page.textContent).toContain('3');
        });

        it('should show snippet when available', () => {
            (component as any).isLoading.set(false);
            (component as any).lectureResults.set([mockResult]);
            fixture.detectChanges();

            const snippet = fixture.nativeElement.querySelector('.lecture-card-content');
            expect(snippet).toBeTruthy();
            expect(snippet.textContent.trim()).toBe('Signals are a reactive primitive in Angular.');
        });

        it('should not show snippet section when snippet is undefined', () => {
            (component as any).isLoading.set(false);
            (component as any).lectureResults.set([mockResultNoSnippet]);
            fixture.detectChanges();

            const snippet = fixture.nativeElement.querySelector('.lecture-card-content');
            expect(snippet).toBeFalsy();
        });

        it('should show no-results message when results are empty and not loading', () => {
            (component as any).isLoading.set(false);
            (component as any).lectureResults.set([]);
            fixture.detectChanges();

            const emptyMessage = fixture.nativeElement.querySelector('.d-block.text-secondary.text-center.py-5');
            expect(emptyMessage).toBeTruthy();
            expect(emptyMessage.textContent).not.toContain('searchLectureContentHint');
        });
    });

    describe('itemCount', () => {
        it('should be 0 when there are no results', () => {
            (component as any).lectureResults.set([]);
            expect(component.itemCount()).toBe(0);
        });

        it('should equal the number of results', () => {
            (component as any).lectureResults.set([mockResult, mockResultNoSnippet]);
            expect(component.itemCount()).toBe(2);
        });
    });

    describe('Back button', () => {
        it('should emit back event when back button is clicked', () => {
            const spy = vi.fn();
            (component as any).back.subscribe(spy);

            const backButton = fixture.nativeElement.querySelector('.back-button');
            backButton.click();

            expect(spy).toHaveBeenCalledOnce();
        });
    });

    describe('Opening a result', () => {
        let jump: ReturnType<typeof vi.spyOn>;

        beforeEach(() => {
            // Stubbed: letting it run would send the test router at a URL no route in this TestBed matches.
            jump = vi.spyOn(TestBed.inject(LectureDeepLinkService), 'jump').mockImplementation(() => {});
            (component as any).lectureResults.set([mockResult]);
            fixture.componentRef.setInput('searchQuery', 'angular');
            fixture.componentRef.setInput('selectedIndex', 0);
            fixture.detectChanges();
        });

        const clickCard = (init: MouseEventInit) => {
            const card = fixture.nativeElement.querySelector('.lecture-result-card') as HTMLAnchorElement;
            const event = new MouseEvent('click', { cancelable: true, ...init });
            card.dispatchEvent(event);
            return { card, event };
        };

        it('should keep a real href, so the browser still offers the result in a new tab', () => {
            const { card } = clickCard({ button: 0 });

            expect(card.getAttribute('href')).toBe('/courses/1/lectures/1/units/1?unit=1&page=3');
        });

        it('should close the overlay, which a same-page jump no longer does via navigation', () => {
            const close = vi.spyOn(TestBed.inject(SearchOverlayService), 'close');

            clickCard({ button: 0 });

            expect(close).toHaveBeenCalled();
        });

        it('should take over a plain left click and jump', () => {
            const { event } = clickCard({ button: 0 });

            expect(event.defaultPrevented).toBe(true);
            expect(jump).toHaveBeenCalledWith(mockResult.lectureUnit.link, expect.objectContaining({ unitId: 1, page: 3 }));
        });

        it.each([
            { name: 'middle button', init: { button: 1 } },
            { name: 'Cmd/Ctrl', init: { button: 0, metaKey: true } },
            { name: 'Shift', init: { button: 0, shiftKey: true } },
        ])('should leave a click with $name to the browser, which opens the href itself', ({ init }) => {
            const { event } = clickCard(init);

            // Preventing the default here would swallow "open in new tab".
            expect(event.defaultPrevented).toBe(false);
            expect(jump).not.toHaveBeenCalled();
        });

        it('should jump when Enter is pressed on the selected result', () => {
            const event = new KeyboardEvent('keydown', { key: 'Enter' });
            const preventDefaultSpy = vi.spyOn(event, 'preventDefault');

            component.handleKeydown(event);

            expect(preventDefaultSpy).toHaveBeenCalled();
            expect(jump).toHaveBeenCalledWith(mockResult.lectureUnit.link, expect.objectContaining({ unitId: 1, page: 3 }));
        });

        it('should not jump when Enter is pressed with no selection', () => {
            fixture.componentRef.setInput('selectedIndex', -1);
            fixture.detectChanges();

            component.handleKeydown(new KeyboardEvent('keydown', { key: 'Enter' }));

            expect(jump).not.toHaveBeenCalled();
        });

        it('should not jump on non-Enter key', () => {
            component.handleKeydown(new KeyboardEvent('keydown', { key: 'ArrowDown' }));

            expect(jump).not.toHaveBeenCalled();
        });
    });

    describe('Search pipeline', () => {
        let pipelineFixture: ComponentFixture<GlobalSearchLectureResultsComponent>;
        let pipelineComponent: GlobalSearchLectureResultsComponent;

        beforeEach(() => {
            // Fake timers must be active before component construction so that
            // RxJS debounceTime uses the fake scheduler from the start.
            vi.useFakeTimers();

            vi.clearAllMocks();
            Element.prototype.scrollIntoView = vi.fn();
            mockSearchService.search.mockReturnValue(new Subject().asObservable());

            pipelineFixture = TestBed.createComponent(GlobalSearchLectureResultsComponent);
            pipelineComponent = pipelineFixture.componentInstance;
            pipelineFixture.componentRef.setInput('searchQuery', '');
            pipelineFixture.componentRef.setInput('selectedIndex', -1);
            pipelineFixture.detectChanges();
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        it('should call the search service with the query after debounce', () => {
            const results = [mockResult];
            mockSearchService.search.mockReturnValue(of(results));

            pipelineFixture.componentRef.setInput('searchQuery', 'signals');
            pipelineFixture.detectChanges();

            vi.advanceTimersByTime(300);
            pipelineFixture.detectChanges();

            expect(mockSearchService.search).toHaveBeenCalledWith('signals');
            expect((pipelineComponent as any).lectureResults()).toEqual(results);
        });

        it('should not call the search service for a whitespace-only query', () => {
            pipelineFixture.componentRef.setInput('searchQuery', '   ');
            pipelineFixture.detectChanges();

            vi.advanceTimersByTime(300);
            pipelineFixture.detectChanges();

            expect(mockSearchService.search).not.toHaveBeenCalled();
        });

        it('should show empty results and set hasError when the search service errors', () => {
            mockSearchService.search.mockReturnValue(throwError(() => new Error('Server error')));

            pipelineFixture.componentRef.setInput('searchQuery', 'bad query');
            pipelineFixture.detectChanges();

            vi.advanceTimersByTime(300);
            pipelineFixture.detectChanges();

            expect((pipelineComponent as any).lectureResults()).toEqual([]);
            expect((pipelineComponent as any).isLoading()).toBe(false);
            expect((pipelineComponent as any).hasError()).toBe(true);

            const errorMessage = pipelineFixture.nativeElement.querySelector('.d-block.text-secondary.text-center.py-5');
            expect(errorMessage).toBeTruthy();
            expect(errorMessage.textContent).not.toContain('noResultsFound');
        });
    });
});
