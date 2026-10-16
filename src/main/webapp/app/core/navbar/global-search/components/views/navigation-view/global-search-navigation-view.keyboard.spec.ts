import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { Subject, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AccountService } from 'app/core/auth/account.service';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { SearchResultItemComponent } from 'app/core/navbar/global-search/components/modal/search-result-item/search-result-item.component';
import { GlobalSearchIrisAnswerComponent } from 'app/core/navbar/global-search/components/views/iris-answer/global-search-iris-answer.component';
import { GlobalSearchNavigationViewComponent } from './global-search-navigation-view.component';
import { IrisSearchAnswerService } from 'app/core/navbar/global-search/services/iris-search-answer.service';
import { SearchOverlayService } from 'app/core/navbar/global-search/services/search-overlay.service';
import { IrisSearchStatusUpdate } from 'app/core/navbar/global-search/models/iris-search-status-update.model';
import { SEARCH_DEBOUNCE_MS } from 'app/core/navbar/global-search/components/views/search-result-view.directive';
import { GlobalSearchResult } from 'app/openapi/model/global-search-result';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

const originalScrollIntoView = HTMLElement.prototype.scrollIntoView;

describe('GlobalSearchNavigationViewComponent keyboard integration', () => {
    let fixture: ComponentFixture<GlobalSearchNavigationViewComponent>;
    let router: Router;
    let overlay: { close: ReturnType<typeof vi.fn> };
    let ask: ReturnType<typeof vi.fn>;
    let askSubject: Subject<IrisSearchStatusUpdate>;

    beforeEach(() => {
        vi.useFakeTimers();
        HTMLElement.prototype.scrollIntoView = vi.fn();
        askSubject = new Subject<IrisSearchStatusUpdate>();
        ask = vi.fn().mockReturnValue(askSubject.asObservable());
        overlay = { close: vi.fn() };

        TestBed.configureTestingModule({
            imports: [GlobalSearchNavigationViewComponent],
            providers: [
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useValue: { isModuleFeatureActive: vi.fn().mockReturnValue(true) } },
                { provide: AccountService, useValue: { userIdentity: signal({ selectedLLMUsage: LLMSelectionDecision.CLOUD_AI }) } },
                { provide: SearchOverlayService, useValue: overlay },
                { provide: IrisSearchAnswerService, useValue: { ask } },
            ],
        });
        TestBed.overrideComponent(GlobalSearchNavigationViewComponent, {
            remove: { imports: [SearchResultItemComponent, ArtemisTranslatePipe] },
            add: { imports: [MockComponent(SearchResultItemComponent), MockPipe(ArtemisTranslatePipe)] },
        });
        TestBed.overrideComponent(GlobalSearchIrisAnswerComponent, {
            remove: { imports: [MarkdownDirective, ArtemisTranslatePipe] },
            add: { imports: [MockDirective(MarkdownDirective), MockPipe(ArtemisTranslatePipe)] },
        });

        fixture = TestBed.createComponent(GlobalSearchNavigationViewComponent);
        router = TestBed.inject(Router);
        fixture.componentRef.setInput('searchQuery', 'A detailed Iris question');
        fixture.componentRef.setInput('showResults', true);
        fixture.componentRef.setInput('selectedIndex', 0);
        fixture.componentRef.setInput('results', [{ id: '42', type: 'exercise', metadata: { courseId: 7 } }] as GlobalSearchResult[]);
        fixture.detectChanges();
        vi.clearAllMocks();
    });

    afterEach(() => {
        HTMLElement.prototype.scrollIntoView = originalScrollIntoView;
        vi.useRealTimers();
    });

    function showEntityCitation(): HTMLElement {
        startIrisQuery();
        askSubject.next({
            runId: 'run-1',
            isThinking: false,
            answer: 'The exercise is relevant.[1]',
            sources: [],
            entitySources: [
                {
                    entityType: 'exercise',
                    exerciseType: 'programming',
                    entityId: 99,
                    title: 'Entity source',
                    link: '/courses/7/exercises/99',
                },
            ],
            citationSourceTypes: ['entity'],
        });
        fixture.detectChanges();
        const answerBody = fixture.nativeElement.querySelector('.iris-answer-text') as HTMLElement;
        answerBody.innerHTML = '<p>The exercise is relevant.<sup class="iris-cite" data-n="1" role="link" tabindex="0">1</sup></p>';
        return answerBody.querySelector('.iris-cite')!;
    }

    function startIrisQuery(): void {
        vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
        fixture.detectChanges();
        expect(ask).toHaveBeenCalledOnce();
    }

    function dispatchEnter(target: EventTarget): KeyboardEvent {
        const event = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
        target.dispatchEvent(event);
        return event;
    }

    it('lets a real inline citation handle Enter once without selecting or closing the result', () => {
        const navigateByUrl = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
        const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        const citation = showEntityCitation();
        citation.focus();
        expect(document.activeElement).toBe(citation);

        const event = dispatchEnter(citation);

        expect(event.defaultPrevented).toBe(true);
        expect(navigateByUrl).toHaveBeenCalledOnce();
        expect(navigateByUrl).toHaveBeenCalledWith('/courses/7/exercises/99');
        expect(navigate).not.toHaveBeenCalled();
        expect(overlay.close).not.toHaveBeenCalled();
    });

    it('leaves an entity source Enter unhandled by the global result navigation and keeps its native activation', () => {
        const navigateByUrl = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
        const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        showEntityCitation();
        const entityButton = fixture.nativeElement.querySelector('[data-testid="iris-entity-chip"]') as HTMLButtonElement;
        entityButton.focus();
        expect(document.activeElement).toBe(entityButton);

        const event = dispatchEnter(entityButton);
        entityButton.click(); // jsdom does not synthesize a click for an uncancelled Enter keydown.

        expect(event.defaultPrevented).toBe(false);
        expect(navigateByUrl).toHaveBeenCalledOnce();
        expect(navigateByUrl).toHaveBeenCalledWith('/courses/7/exercises/99');
        expect(navigate).not.toHaveBeenCalled();
        expect(overlay.close).not.toHaveBeenCalled();
    });

    it('keeps retry Enter available to its native button and reissues the Iris ask', () => {
        ask.mockReturnValueOnce(throwError(() => new Error('Iris unavailable'))).mockReturnValueOnce(askSubject.asObservable());
        startIrisQuery();

        const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        const retry = fixture.nativeElement.querySelector('[data-testid="iris-answer-retry"]') as HTMLButtonElement;
        retry.focus();
        expect(document.activeElement).toBe(retry);
        const event = dispatchEnter(retry);
        retry.click(); // jsdom does not synthesize a click for an uncancelled Enter keydown.
        vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
        fixture.detectChanges();

        expect(event.defaultPrevented).toBe(false);
        expect(ask).toHaveBeenCalledTimes(2);
        expect(navigate).not.toHaveBeenCalled();
        expect(overlay.close).not.toHaveBeenCalled();
    });

    it('keeps ordinary selected-result Enter navigation and ignores an already-cancelled or non-Enter event', () => {
        const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        const prevented = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true });
        prevented.preventDefault();
        window.dispatchEvent(prevented);
        window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', cancelable: true }));

        expect(navigate).not.toHaveBeenCalled();
        expect(overlay.close).not.toHaveBeenCalled();

        const event = dispatchEnter(document.body);

        expect(event.defaultPrevented).toBe(true);
        expect(navigate).toHaveBeenCalledOnce();
        expect(navigate).toHaveBeenCalledWith(['/courses', 7, 'exercises', '42']);
        expect(overlay.close).toHaveBeenCalledOnce();
    });
});
