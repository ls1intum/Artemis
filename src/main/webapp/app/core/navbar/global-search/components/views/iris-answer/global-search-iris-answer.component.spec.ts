import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockPipe } from 'ng-mocks';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { IrisCitationTextComponent } from 'app/iris/overview/citation-text/iris-citation-text.component';
import { GlobalSearchIrisAnswerComponent } from './global-search-iris-answer.component';
import { LectureSearchService } from 'app/core/navbar/global-search/services/lecture-search.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { IrisSearchWebsocketDTO } from 'app/core/navbar/global-search/models/iris-search-result.model';

const mockSource = {
    course: { id: 1, name: 'Web Development' },
    lecture: { id: 1, name: 'Angular Basics' },
    lectureUnit: { id: 1, name: 'Signals Introduction', link: '/courses/1/lectures/1/units/1', pageNumber: 3 },
    snippet: 'Signals are reactive.',
};

const mockPartialDTO: IrisSearchWebsocketDTO = {
    cited: false,
    answer: 'Signals are a reactive primitive.',
    sources: [mockSource],
};

const mockCitedDTO: IrisSearchWebsocketDTO = {
    cited: true,
    answer: 'Signals are a reactive primitive [cite:L:1:3:::keyword:summary].',
    sources: [mockSource],
};

const mockSearchService = { search: vi.fn(), ask: vi.fn() };
const wsSubject = new Subject<IrisSearchWebsocketDTO>();
const mockWebsocketService = { subscribe: vi.fn().mockReturnValue(wsSubject.asObservable()) };

describe('GlobalSearchIrisAnswerComponent', () => {
    setupTestBed({ zoneless: true });

    let component: GlobalSearchIrisAnswerComponent;
    let fixture: ComponentFixture<GlobalSearchIrisAnswerComponent>;

    beforeEach(() => {
        vi.clearAllMocks();
        Element.prototype.scrollIntoView = vi.fn();
        Element.prototype.scrollTo = vi.fn() as typeof Element.prototype.scrollTo;
        mockSearchService.ask.mockReturnValue(new Subject().asObservable());
        mockWebsocketService.subscribe.mockReturnValue(wsSubject.asObservable());

        TestBed.configureTestingModule({
            imports: [GlobalSearchIrisAnswerComponent, MockPipe(ArtemisTranslatePipe), MockComponent(IrisLogoComponent), MockComponent(IrisCitationTextComponent)],
            providers: [
                provideRouter([]),
                { provide: LectureSearchService, useValue: mockSearchService },
                { provide: WebsocketService, useValue: mockWebsocketService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
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
        it('should show loading skeleton when loadingState is loading', () => {
            (component as any).loadingState.set('loading');
            fixture.detectChanges();

            const loading = fixture.nativeElement.querySelector('.iris-loading');
            expect(loading).toBeTruthy();
        });

        it('should hide loading skeleton when loadingState is idle', () => {
            (component as any).loadingState.set('idle');
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

    describe('Partial state (cited=false)', () => {
        beforeEach(() => {
            (component as any).loadingState.set('partial');
            (component as any).sources.set(mockPartialDTO.sources);
            fixture.detectChanges();
        });

        it('should show answer text area', () => {
            const answerText = fixture.nativeElement.querySelector('.iris-answer-text');
            expect(answerText).toBeTruthy();
        });

        it('should show source cards', () => {
            const sourceCards = fixture.nativeElement.querySelectorAll('.iris-source-card');
            expect(sourceCards).toHaveLength(1);
        });

        it('should display source name, course, lecture, and page number', () => {
            const card = fixture.nativeElement.querySelector('.iris-source-card');
            expect(card.querySelector('.iris-source-title').textContent.trim()).toBe('Signals Introduction');
            const meta = card.querySelector('.iris-source-meta');
            expect(meta.textContent).toContain('Web Development');
            expect(meta.textContent).toContain('Angular Basics');
            expect(meta.textContent).toContain('3');
        });

        it('should apply is-selected class to the selected source card', () => {
            fixture.componentRef.setInput('selectedSourceIndex', 0);
            fixture.detectChanges();

            const card = fixture.nativeElement.querySelector('.iris-source-card');
            expect(card.classList.contains('is-selected')).toBe(true);
        });

        it('should not apply is-selected class when index does not match', () => {
            fixture.componentRef.setInput('selectedSourceIndex', -1);
            fixture.detectChanges();

            const card = fixture.nativeElement.querySelector('.iris-source-card');
            expect(card.classList.contains('is-selected')).toBe(false);
        });
    });

    describe('Complete state (cited=true)', () => {
        beforeEach(() => {
            (component as any).loadingState.set('complete');
            (component as any).citedText.set(mockCitedDTO.answer);
            (component as any).sources.set(mockCitedDTO.sources);
            fixture.detectChanges();
        });

        it('should show the citation text component', () => {
            const citationText = fixture.nativeElement.querySelector('jhi-iris-citation-text');
            expect(citationText).toBeTruthy();
        });

        it('should show source cards', () => {
            const sourceCards = fixture.nativeElement.querySelectorAll('.iris-source-card');
            expect(sourceCards).toHaveLength(1);
        });

        it('should not show sources section when sources array is empty', () => {
            (component as any).sources.set([]);
            fixture.detectChanges();

            const sourcesList = fixture.nativeElement.querySelector('.iris-sources-list');
            expect(sourcesList).toBeFalsy();
        });
    });

    describe('itemCount', () => {
        it('should be 0 when sources is empty', () => {
            (component as any).sources.set([]);
            expect(component.itemCount()).toBe(0);
        });

        it('should equal the number of sources', () => {
            (component as any).sources.set([mockSource, mockSource]);
            expect(component.itemCount()).toBe(2);
        });
    });

    describe('Keyboard navigation', () => {
        it('should navigate to lecture deep link when Enter is pressed on a selected source', () => {
            (component as any).sources.set([mockSource]);
            fixture.componentRef.setInput('selectedSourceIndex', 0);
            fixture.detectChanges();

            const navigateSpy = vi.spyOn((component as any).router, 'navigate');
            const event = new KeyboardEvent('keydown', { key: 'Enter' });
            const preventDefaultSpy = vi.spyOn(event, 'preventDefault');

            component.handleKeydown(event);

            expect(preventDefaultSpy).toHaveBeenCalled();
            expect(navigateSpy).toHaveBeenCalledWith(['/courses', 1, 'lectures', 1], {
                queryParams: { unit: 1, page: 3, timestamp: undefined },
            });
        });

        it('should not navigate when Enter is pressed with no selection', () => {
            (component as any).sources.set([mockSource]);
            fixture.componentRef.setInput('selectedSourceIndex', -1);
            fixture.detectChanges();

            const navigateSpy = vi.spyOn((component as any).router, 'navigate');
            const event = new KeyboardEvent('keydown', { key: 'Enter' });

            component.handleKeydown(event);

            expect(navigateSpy).not.toHaveBeenCalled();
        });

        it('should not navigate on non-Enter key', () => {
            (component as any).sources.set([mockSource]);
            fixture.componentRef.setInput('selectedSourceIndex', 0);
            fixture.detectChanges();

            const navigateSpy = vi.spyOn((component as any).router, 'navigate');
            const event = new KeyboardEvent('keydown', { key: 'ArrowDown' });

            component.handleKeydown(event);

            expect(navigateSpy).not.toHaveBeenCalled();
        });
    });

    describe('Search pipeline', () => {
        let pipelineFixture: ComponentFixture<GlobalSearchIrisAnswerComponent>;
        let pipelineComponent: GlobalSearchIrisAnswerComponent;
        let wsChannel: Subject<IrisSearchWebsocketDTO>;

        beforeEach(() => {
            vi.useFakeTimers();

            vi.clearAllMocks();
            Element.prototype.scrollIntoView = vi.fn();
            Element.prototype.scrollTo = vi.fn() as typeof Element.prototype.scrollTo;

            wsChannel = new Subject<IrisSearchWebsocketDTO>();
            mockSearchService.ask.mockReturnValue(new Subject().asObservable());
            mockWebsocketService.subscribe.mockReturnValue(wsChannel.asObservable());

            pipelineFixture = TestBed.createComponent(GlobalSearchIrisAnswerComponent);
            pipelineComponent = pipelineFixture.componentInstance;
            pipelineFixture.componentRef.setInput('query', '');
            pipelineFixture.componentRef.setInput('selectedSourceIndex', -1);
            pipelineFixture.detectChanges();
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        it('should subscribe to WebSocket after receiving token from ask service', () => {
            mockSearchService.ask.mockReturnValue(of({ token: 'test-token' }));

            pipelineFixture.componentRef.setInput('query', 'signals');
            pipelineFixture.detectChanges();
            vi.advanceTimersByTime(300);
            pipelineFixture.detectChanges();

            expect(mockSearchService.ask).toHaveBeenCalledWith('signals');
            expect(mockWebsocketService.subscribe).toHaveBeenCalledWith('/user/topic/iris/search/test-token');
        });

        it('should set partial state on cited=false WS message', () => {
            mockSearchService.ask.mockReturnValue(of({ token: 'tok1' }));

            pipelineFixture.componentRef.setInput('query', 'signals');
            pipelineFixture.detectChanges();
            vi.advanceTimersByTime(300);
            pipelineFixture.detectChanges();

            wsChannel.next(mockPartialDTO);
            pipelineFixture.detectChanges();

            expect((pipelineComponent as any).loadingState()).toBe('partial');
            expect((pipelineComponent as any).sources()).toEqual(mockPartialDTO.sources);
        });

        it('should set complete state on cited=true WS message', () => {
            mockSearchService.ask.mockReturnValue(of({ token: 'tok2' }));

            pipelineFixture.componentRef.setInput('query', 'signals');
            pipelineFixture.detectChanges();
            vi.advanceTimersByTime(300);
            pipelineFixture.detectChanges();

            wsChannel.next(mockCitedDTO);
            pipelineFixture.detectChanges();

            expect((pipelineComponent as any).loadingState()).toBe('complete');
            expect((pipelineComponent as any).citedText()).toBe(mockCitedDTO.answer);
        });

        it('should not call ask service for a whitespace-only query', () => {
            pipelineFixture.componentRef.setInput('query', '   ');
            pipelineFixture.detectChanges();
            vi.advanceTimersByTime(300);
            pipelineFixture.detectChanges();

            expect(mockSearchService.ask).not.toHaveBeenCalled();
        });

        it('should set hasError and idle state when the ask service errors', () => {
            mockSearchService.ask.mockReturnValue(throwError(() => new Error('Server error')));

            pipelineFixture.componentRef.setInput('query', 'bad query');
            pipelineFixture.detectChanges();
            vi.advanceTimersByTime(300);
            pipelineFixture.detectChanges();

            expect((pipelineComponent as any).loadingState()).toBe('idle');
            expect((pipelineComponent as any).hasError()).toBe(true);

            const errorMsg = pipelineFixture.nativeElement.querySelector('.iris-error-msg');
            expect(errorMsg).toBeTruthy();
        });
    });
});
