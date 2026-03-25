import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockPipe } from 'ng-mocks';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { GlobalSearchIrisAnswerComponent } from './global-search-iris-answer.component';
import { LectureSearchService } from 'app/core/navbar/global-search/services/lecture-search.service';
import { IrisSearchResult } from 'app/core/navbar/global-search/models/iris-search-result.model';

const mockSource = {
    course: { id: 1, name: 'Web Development' },
    lecture: { id: 1, name: 'Angular Basics' },
    lectureUnit: { id: 1, name: 'Signals Introduction', link: '/courses/1/lectures/1/units/1', pageNumber: 3 },
    snippet: 'Signals are reactive.',
};

const mockResult: IrisSearchResult = {
    answer: '## Answer\nSignals are a reactive primitive.',
    sources: [mockSource],
};

const mockResultNoSources: IrisSearchResult = {
    answer: 'A simple answer with no sources.',
    sources: [],
};

const mockSearchService = { search: vi.fn(), ask: vi.fn() };

describe('GlobalSearchIrisAnswerComponent', () => {
    setupTestBed({ zoneless: true });

    let component: GlobalSearchIrisAnswerComponent;
    let fixture: ComponentFixture<GlobalSearchIrisAnswerComponent>;

    beforeEach(() => {
        vi.clearAllMocks();
        Element.prototype.scrollIntoView = vi.fn();
        Element.prototype.scrollTo = vi.fn() as typeof Element.prototype.scrollTo;
        mockSearchService.ask.mockReturnValue(new Subject().asObservable());

        TestBed.configureTestingModule({
            imports: [GlobalSearchIrisAnswerComponent, MockPipe(ArtemisTranslatePipe), MockPipe(HtmlForMarkdownPipe), MockComponent(IrisLogoComponent)],
            providers: [provideRouter([]), { provide: LectureSearchService, useValue: mockSearchService }, { provide: TranslateService, useClass: MockTranslateService }],
        });

        fixture = TestBed.createComponent(GlobalSearchIrisAnswerComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('query', '');
        fixture.componentRef.setInput('selectedSourceIndex', -1);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('Close button', () => {
        it('should emit closeDrawer when clicked', () => {
            const spy = vi.fn();
            component.closeDrawer.subscribe(spy);

            const closeBtn = fixture.nativeElement.querySelector('.iris-close-btn');
            closeBtn.click();

            expect(spy).toHaveBeenCalledOnce();
        });
    });

    describe('Loading state', () => {
        it('should show loading indicator when isLoading is true', () => {
            (component as any).isLoading.set(true);
            fixture.detectChanges();

            const loading = fixture.nativeElement.querySelector('.iris-loading');
            expect(loading).toBeTruthy();
        });

        it('should hide loading indicator when isLoading is false', () => {
            (component as any).isLoading.set(false);
            fixture.detectChanges();

            const loading = fixture.nativeElement.querySelector('.iris-loading');
            expect(loading).toBeFalsy();
        });
    });

    describe('Error state', () => {
        it('should show error message when hasError is true', () => {
            (component as any).hasError.set(true);
            fixture.detectChanges();

            const errorMsg = fixture.nativeElement.querySelector('.iris-error-msg');
            expect(errorMsg).toBeTruthy();
        });

        it('should not show error message when hasError is false', () => {
            (component as any).hasError.set(false);
            fixture.detectChanges();

            const errorMsg = fixture.nativeElement.querySelector('.iris-error-msg');
            expect(errorMsg).toBeFalsy();
        });
    });

    describe('Result rendering', () => {
        it('should show answer text when result is available', () => {
            (component as any).result.set(mockResult);
            fixture.detectChanges();

            const answerText = fixture.nativeElement.querySelector('.iris-answer-text');
            expect(answerText).toBeTruthy();
        });

        it('should show source cards when result has sources', () => {
            (component as any).result.set(mockResult);
            fixture.detectChanges();

            const sourceCards = fixture.nativeElement.querySelectorAll('.iris-source-card');
            expect(sourceCards).toHaveLength(1);
        });

        it('should display source name, course, lecture, and page number', () => {
            (component as any).result.set(mockResult);
            fixture.detectChanges();

            const card = fixture.nativeElement.querySelector('.iris-source-card');
            expect(card.querySelector('.iris-source-title').textContent.trim()).toBe('Signals Introduction');
            const meta = card.querySelector('.iris-source-meta');
            expect(meta.textContent).toContain('Web Development');
            expect(meta.textContent).toContain('Angular Basics');
            expect(meta.textContent).toContain('3');
        });

        it('should apply is-selected class to the selected source card', () => {
            (component as any).result.set(mockResult);
            fixture.componentRef.setInput('selectedSourceIndex', 0);
            fixture.detectChanges();

            const card = fixture.nativeElement.querySelector('.iris-source-card');
            expect(card.classList.contains('is-selected')).toBe(true);
        });

        it('should not apply is-selected class when index does not match', () => {
            (component as any).result.set(mockResult);
            fixture.componentRef.setInput('selectedSourceIndex', -1);
            fixture.detectChanges();

            const card = fixture.nativeElement.querySelector('.iris-source-card');
            expect(card.classList.contains('is-selected')).toBe(false);
        });

        it('should not show sources section when sources array is empty', () => {
            (component as any).result.set(mockResultNoSources);
            fixture.detectChanges();

            const sourcesList = fixture.nativeElement.querySelector('.iris-sources-list');
            expect(sourcesList).toBeFalsy();
        });
    });

    describe('itemCount', () => {
        it('should be 0 when result is undefined', () => {
            (component as any).result.set(undefined);
            expect(component.itemCount()).toBe(0);
        });

        it('should equal the number of sources', () => {
            (component as any).result.set({ ...mockResult, sources: [mockSource, mockSource] });
            expect(component.itemCount()).toBe(2);
        });
    });

    describe('Keyboard navigation', () => {
        it('should navigate to source link when Enter is pressed on a selected source', () => {
            (component as any).result.set(mockResult);
            fixture.componentRef.setInput('selectedSourceIndex', 0);
            fixture.detectChanges();

            const navigateSpy = vi.spyOn((component as any).router, 'navigateByUrl');
            const event = new KeyboardEvent('keydown', { key: 'Enter' });
            const preventDefaultSpy = vi.spyOn(event, 'preventDefault');

            component.handleKeydown(event);

            expect(preventDefaultSpy).toHaveBeenCalled();
            expect(navigateSpy).toHaveBeenCalledWith('/courses/1/lectures/1/units/1');
        });

        it('should not navigate when Enter is pressed with no selection', () => {
            (component as any).result.set(mockResult);
            fixture.componentRef.setInput('selectedSourceIndex', -1);
            fixture.detectChanges();

            const navigateSpy = vi.spyOn((component as any).router, 'navigateByUrl');
            const event = new KeyboardEvent('keydown', { key: 'Enter' });

            component.handleKeydown(event);

            expect(navigateSpy).not.toHaveBeenCalled();
        });

        it('should not navigate on non-Enter key', () => {
            (component as any).result.set(mockResult);
            fixture.componentRef.setInput('selectedSourceIndex', 0);
            fixture.detectChanges();

            const navigateSpy = vi.spyOn((component as any).router, 'navigateByUrl');
            const event = new KeyboardEvent('keydown', { key: 'ArrowDown' });

            component.handleKeydown(event);

            expect(navigateSpy).not.toHaveBeenCalled();
        });
    });

    describe('Search pipeline', () => {
        let pipelineFixture: ComponentFixture<GlobalSearchIrisAnswerComponent>;
        let pipelineComponent: GlobalSearchIrisAnswerComponent;

        beforeEach(() => {
            // Fake timers must be active before component construction so that
            // RxJS debounceTime uses the fake scheduler from the start.
            vi.useFakeTimers();

            vi.clearAllMocks();
            Element.prototype.scrollIntoView = vi.fn();
            Element.prototype.scrollTo = vi.fn() as typeof Element.prototype.scrollTo;
            mockSearchService.ask.mockReturnValue(new Subject().asObservable());

            pipelineFixture = TestBed.createComponent(GlobalSearchIrisAnswerComponent);
            pipelineComponent = pipelineFixture.componentInstance;
            pipelineFixture.componentRef.setInput('query', '');
            pipelineFixture.componentRef.setInput('selectedSourceIndex', -1);
            pipelineFixture.detectChanges();
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        it('should call the ask service with the query after debounce', () => {
            mockSearchService.ask.mockReturnValue(of(mockResult));

            pipelineFixture.componentRef.setInput('query', 'signals');
            pipelineFixture.detectChanges();

            vi.advanceTimersByTime(300);
            pipelineFixture.detectChanges();

            expect(mockSearchService.ask).toHaveBeenCalledWith('signals');
            expect((pipelineComponent as any).result()).toEqual(mockResult);
        });

        it('should not call the ask service for a whitespace-only query', () => {
            pipelineFixture.componentRef.setInput('query', '   ');
            pipelineFixture.detectChanges();

            vi.advanceTimersByTime(300);
            pipelineFixture.detectChanges();

            expect(mockSearchService.ask).not.toHaveBeenCalled();
        });

        it('should set hasError and clear result when the ask service errors', () => {
            mockSearchService.ask.mockReturnValue(throwError(() => new Error('Server error')));

            pipelineFixture.componentRef.setInput('query', 'bad query');
            pipelineFixture.detectChanges();

            vi.advanceTimersByTime(300);
            pipelineFixture.detectChanges();

            expect((pipelineComponent as any).result()).toBeUndefined();
            expect((pipelineComponent as any).isLoading()).toBe(false);
            expect((pipelineComponent as any).hasError()).toBe(true);

            const errorMsg = pipelineFixture.nativeElement.querySelector('.iris-error-msg');
            expect(errorMsg).toBeTruthy();
        });
    });
});
