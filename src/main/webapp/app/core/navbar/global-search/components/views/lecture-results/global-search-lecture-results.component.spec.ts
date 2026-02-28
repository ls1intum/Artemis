import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockPipe } from 'ng-mocks';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { GlobalSearchLectureResultsComponent } from './global-search-lecture-results.component';
import { LectureSearchService } from 'app/core/navbar/global-search/services/lecture-search.service';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';

const mockResult: LectureSearchResult = {
    lectureUnitId: 1,
    lectureUnitName: 'Introduction to Signals',
    lectureUnitLink: '/courses/1/lectures/1/units/1',
    lectureId: 1,
    lectureName: 'Angular Basics',
    courseId: 1,
    courseName: 'Advanced Web Development',
    pageNumber: 3,
    baseUrl: 'http://localhost',
    snippet: 'Signals are a reactive primitive in Angular.',
};

const mockResultNoSnippet: LectureSearchResult = {
    lectureUnitId: 2,
    lectureUnitName: 'Spring Boot Overview',
    lectureUnitLink: '/courses/2/lectures/2/units/2',
    lectureId: 2,
    lectureName: 'Backend Fundamentals',
    courseId: 2,
    courseName: 'Server-Side Development',
    pageNumber: 7,
    baseUrl: 'http://localhost',
};

const mockSearchService = { search: vi.fn() };

describe('GlobalSearchLectureResultsComponent', () => {
    setupTestBed({ zoneless: true });

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

    describe('Keyboard navigation', () => {
        it('should navigate to result link when Enter is pressed on a selected result', () => {
            (component as any).lectureResults.set([mockResult]);
            fixture.componentRef.setInput('selectedIndex', 0);
            fixture.detectChanges();

            const navigateSpy = vi.spyOn((component as any).router, 'navigateByUrl');
            const event = new KeyboardEvent('keydown', { key: 'Enter' });
            const preventDefaultSpy = vi.spyOn(event, 'preventDefault');

            component.handleKeydown(event);

            expect(preventDefaultSpy).toHaveBeenCalled();
            expect(navigateSpy).toHaveBeenCalledWith('/courses/1/lectures/1/units/1');
        });

        it('should not navigate when Enter is pressed with no selection', () => {
            (component as any).lectureResults.set([mockResult]);
            fixture.componentRef.setInput('selectedIndex', -1);
            fixture.detectChanges();

            const navigateSpy = vi.spyOn((component as any).router, 'navigateByUrl');
            const event = new KeyboardEvent('keydown', { key: 'Enter' });

            component.handleKeydown(event);

            expect(navigateSpy).not.toHaveBeenCalled();
        });

        it('should not navigate on non-Enter key', () => {
            (component as any).lectureResults.set([mockResult]);
            fixture.componentRef.setInput('selectedIndex', 0);
            fixture.detectChanges();

            const navigateSpy = vi.spyOn((component as any).router, 'navigateByUrl');
            const event = new KeyboardEvent('keydown', { key: 'ArrowDown' });

            component.handleKeydown(event);

            expect(navigateSpy).not.toHaveBeenCalled();
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

        it('should show empty results when the search service errors', () => {
            mockSearchService.search.mockReturnValue(throwError(() => new Error('Server error')));

            pipelineFixture.componentRef.setInput('searchQuery', 'bad query');
            pipelineFixture.detectChanges();

            vi.advanceTimersByTime(300);
            pipelineFixture.detectChanges();

            expect((pipelineComponent as any).lectureResults()).toEqual([]);
            expect((pipelineComponent as any).isLoading()).toBe(false);
        });
    });
});
