import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MockPipe } from 'ng-mocks';
import { Subject } from 'rxjs';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { IrisSearchAnswerService } from 'app/core/navbar/global-search/services/iris-search-answer.service';
import { GlobalSearchIrisAnswerComponent } from './global-search-iris-answer.component';
import { IrisSearchStatusUpdate } from 'app/core/navbar/global-search/models/iris-search-status-update.model';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { SEARCH_DEBOUNCE_MS } from 'app/core/navbar/global-search/components/views/search-result-view.directive';

const SOURCE: LectureSearchResult = {
    course: { id: 1, name: 'Secure Search' },
    lecture: { id: 2, name: 'Sanitization' },
    lectureUnit: {
        id: 3,
        name: 'Trusted source',
        link: '/lectures/2/units/3',
        pageNumber: 1,
        sourceType: 'lecture_unit_slide',
        queryParams: { unit: 3, page: 1 },
    },
};

const MALICIOUS_HTML =
    '<script>window.irisScriptExecuted = true</script><img src="x" onerror="window.irisImageExecuted = true"><svg onload="window.irisSvgExecuted = true"></svg><a href="javascript:alert(1)"></a>';

/**
 * Exercises the real lazy MarkdownDirective boundary: raw answer markup reaches
 * markdown-it, DOMPurify removes unsafe content, and only then Angular trusts the result.
 */
describe('GlobalSearchIrisAnswerComponent markdown sanitization', () => {
    let fixture: ComponentFixture<GlobalSearchIrisAnswerComponent>;
    let askSubject: Subject<IrisSearchStatusUpdate>;
    let ask: ReturnType<typeof vi.fn>;

    const originalScrollIntoView = Element.prototype.scrollIntoView;

    beforeAll(() => {
        Element.prototype.scrollIntoView = vi.fn();
    });

    afterAll(() => {
        Element.prototype.scrollIntoView = originalScrollIntoView;
    });

    beforeEach(() => {
        vi.useFakeTimers();
        askSubject = new Subject<IrisSearchStatusUpdate>();
        ask = vi.fn().mockReturnValue(askSubject.asObservable());

        TestBed.configureTestingModule({
            imports: [GlobalSearchIrisAnswerComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }, { provide: IrisSearchAnswerService, useValue: { ask } }],
        });

        fixture = TestBed.createComponent(GlobalSearchIrisAnswerComponent);
        fixture.componentRef.setInput('searchQuery', 'Explain how Iris keeps lecture answers safe');
        fixture.detectChanges();
        vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS + 300);
        fixture.detectChanges();
        expect(ask).toHaveBeenCalledOnce();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    function markdownDirective(): MarkdownDirective {
        const element = fixture.debugElement.query(By.directive(MarkdownDirective));
        expect(element).toBeTruthy();
        return element.injector.get(MarkdownDirective);
    }

    function waitForMarkdownRender(directive: MarkdownDirective): Promise<void> {
        return new Promise<void>((resolve) => directive.markdownRendered.subscribe(resolve));
    }

    function answerBody(): HTMLElement {
        const host: HTMLElement = fixture.nativeElement;
        const body = host.querySelector<HTMLElement>('.iris-answer-text');
        if (!body) {
            throw new Error('Expected the Iris answer body to be rendered');
        }
        return body;
    }

    function expectUnsafeMarkupRemoved(body: HTMLElement): void {
        expect(body.querySelector('script')).toBeNull();
        expect(body.querySelector('[onerror]')).toBeNull();
        expect(body.querySelector('[onload]')).toBeNull();
        expect(body.querySelector('a[href^="javascript:"]')).toBeNull();
    }

    function expectUnsafeMarkupReachesDirective(directive: MarkdownDirective): void {
        const markdown = directive.jhiMarkdown();
        expect(markdown).toContain('<script>');
        expect(markdown).toContain('onerror=');
        expect(markdown).toContain('onload=');
        expect(markdown).toContain('href="javascript:');
    }

    it('sanitizes a terminal answer before trusting it and preserves a usable citation', async () => {
        askSubject.next({ runId: 'run-1', isThinking: true });
        fixture.detectChanges();
        const directive = markdownDirective();
        const rendered = waitForMarkdownRender(directive);
        askSubject.next({
            runId: 'run-1',
            isThinking: false,
            answer: `Safe final answer [1]. ${MALICIOUS_HTML}`,
            sources: [SOURCE],
            citationSourceTypes: ['lecture'],
        });
        fixture.detectChanges();

        expect(directive.jhiMarkdown()).toContain(MALICIOUS_HTML);
        expectUnsafeMarkupReachesDirective(directive);

        await rendered;
        fixture.detectChanges();
        const body = answerBody();
        expectUnsafeMarkupRemoved(body);
        expect(body.textContent).toContain('Safe final answer');

        const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
        const citation = body.querySelector<HTMLElement>('.iris-cite');
        if (!citation) {
            throw new Error('Expected a retained inline citation');
        }
        citation.click();
        expect(navigate).toHaveBeenCalledWith(['/lectures/2/units/3'], {
            queryParams: { unit: 3, page: 1 },
            state: { lectureDeepLink: true },
        });
    });

    it('sanitizes a malicious streamed tail before the terminal answer settles', async () => {
        askSubject.next({ runId: 'run-1', isThinking: true });
        fixture.detectChanges();
        const directive = markdownDirective();
        const renderedStream = waitForMarkdownRender(directive);
        askSubject.next({
            runId: 'run-1',
            isThinking: true,
            partialResult: `Safe streamed words [1] ${MALICIOUS_HTML}`,
            partialSeq: 1,
        });
        vi.runAllTimers();
        fixture.detectChanges();

        expectUnsafeMarkupReachesDirective(directive);
        const animatedTailStart = directive.jhiMarkdown()!.indexOf('<span class="iris-answer-tail">');
        expect(animatedTailStart).toBeGreaterThan(-1);
        expect(directive.jhiMarkdown()!.slice(animatedTailStart)).toContain('href="javascript:alert(1)"');

        await renderedStream;
        fixture.detectChanges();
        const streamedBody = answerBody();
        expect(streamedBody.querySelector('.iris-answer-tail')).toBeTruthy();
        expect(streamedBody.textContent).toContain('Safe streamed words');
        expectUnsafeMarkupRemoved(streamedBody);

        const renderedFinal = waitForMarkdownRender(directive);
        askSubject.next({ runId: 'run-1', isThinking: false, answer: 'Safe final answer [1].', sources: [SOURCE], citationSourceTypes: ['lecture'] });
        vi.runAllTimers();
        fixture.detectChanges();
        await renderedFinal;
        fixture.detectChanges();

        expect(answerBody().textContent).toContain('Safe final answer');
    });
});
