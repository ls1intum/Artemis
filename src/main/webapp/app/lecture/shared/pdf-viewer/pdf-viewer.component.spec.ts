import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PdfViewerComponent } from './pdf-viewer.component';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { PdfEngineService } from 'app/core/pdf/pdf-engine.service';
import { MockPdfEngineService, createMockPdfDocument } from 'test/helpers/mocks/service/mock-pdf-engine.service';
import { PdfActionType, PdfAnnotationSubtype } from '@embedpdf/models';

describe('PdfViewerComponent', () => {
    setupTestBed({ zoneless: true });

    let component: PdfViewerComponent;
    let fixture: ComponentFixture<PdfViewerComponent>;
    let httpMock: HttpTestingController;
    let engineService: MockPdfEngineService;
    let mockThemeService: { currentTheme: ReturnType<typeof signal<Theme>> };
    let originalIntersectionObserver: typeof IntersectionObserver;
    // Every IntersectionObserver constructed by the component, in creation order. observePages() builds the
    // renderObserver first and the current-page (intersection) observer second, so the last two entries are the
    // observers for the most recent observePages() call.
    let observers: { callback: IntersectionObserverCallback; observed: Element[] }[];

    const PDF_URL = 'https://example.com/lecture.pdf';
    const task = <R>(value: R) => ({ toPromise: () => Promise.resolve(value), wait: (cb: (v: R) => void) => cb(value), abort: () => {} });

    /** Builds an IntersectionObserverEntry-like object for the given page element and intersection ratio. */
    function entryFor(target: Element, ratio: number): IntersectionObserverEntry {
        return { target, isIntersecting: ratio > 0, intersectionRatio: ratio } as unknown as IntersectionObserverEntry;
    }

    beforeEach(async () => {
        // jsdom does not implement scrollIntoView (used by goToPage); canvas 2d is provided by vitest-canvas-mock.
        HTMLElement.prototype.scrollIntoView = vi.fn();

        // Capture every IntersectionObserver the component creates so tests can drive its callback manually
        // (jsdom's stub never fires). Restored in afterEach.
        observers = [];
        originalIntersectionObserver = globalThis.IntersectionObserver;
        globalThis.IntersectionObserver = class {
            observed: Element[] = [];
            constructor(public callback: IntersectionObserverCallback) {
                observers.push(this);
            }
            observe(element: Element): void {
                this.observed.push(element);
            }
            unobserve(): void {}
            disconnect(): void {}
            takeRecords(): IntersectionObserverEntry[] {
                return [];
            }
        } as unknown as typeof IntersectionObserver;

        mockThemeService = { currentTheme: signal(Theme.LIGHT) };
        engineService = new MockPdfEngineService();

        await TestBed.configureTestingModule({
            imports: [PdfViewerComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ThemeService, useValue: mockThemeService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: PdfEngineService, useValue: engineService },
            ],
        }).compileComponents();

        httpMock = TestBed.inject(HttpTestingController);
        fixture = TestBed.createComponent(PdfViewerComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
        globalThis.IntersectionObserver = originalIntersectionObserver;
    });

    /** Drains the chained microtasks/macrotasks of the async load + render pipeline. */
    async function flushAsync(): Promise<void> {
        for (let i = 0; i < 8; i++) {
            await new Promise<void>((resolve) => setTimeout(resolve, 0));
            await fixture.whenStable();
        }
    }

    /** Sets the PDF URL, flushes the HTTP blob, and waits for the engine load + render to settle. */
    async function loadPdf(pageCount = 3): Promise<void> {
        engineService.engine.openDocumentBuffer.mockReturnValue(task(createMockPdfDocument('doc', pageCount)) as any);
        fixture.componentRef.setInput('pdfUrl', PDF_URL);
        fixture.detectChanges();
        httpMock.expectOne(PDF_URL).flush(new Blob(['%PDF-1.7'], { type: 'application/pdf' }));
        await flushAsync();
        fixture.detectChanges();
    }

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should fetch, open, and render the initial pages to canvas', async () => {
        await loadPdf(3);

        expect(engineService.engine.openDocumentBuffer).toHaveBeenCalledOnce();
        // Pages are rendered directly to canvas via renderPageRaw (no blob/img); the first pages render eagerly.
        expect(engineService.engine.renderPageRaw).toHaveBeenCalled();
        expect(engineService.engine.renderPage).not.toHaveBeenCalled();
        expect(component['totalPages']()).toBe(3);
        // A sized slot exists for every page even though only the visible/eager ones are drawn.
        expect(component['renderedPages']().length).toBe(3);
    });

    it('should emit pageRendered after a successful load', async () => {
        const emitted = vi.fn();
        component.pageRendered.subscribe(emitted);

        await loadPdf();

        expect(emitted).toHaveBeenCalledWith({ pdfUrl: PDF_URL });
        expect(component['isLoading']()).toBe(false);
    });

    it('should emit loadError when opening the document fails', async () => {
        const emitted = vi.fn();
        component.loadError.subscribe(emitted);
        engineService.engine.openDocumentBuffer.mockReturnValue({ toPromise: () => Promise.reject(new Error('boom')), wait: () => {}, abort: () => {} } as any);

        fixture.componentRef.setInput('pdfUrl', PDF_URL);
        fixture.detectChanges();
        httpMock.expectOne(PDF_URL).flush(new Blob(['x'], { type: 'application/pdf' }));
        await flushAsync();

        expect(emitted).toHaveBeenCalledWith({ pdfUrl: PDF_URL });
    });

    it('should run a search through the engine and expose the match count', async () => {
        await loadPdf();
        engineService.engine.searchAllPages.mockReturnValue(
            task({ results: [{ pageIndex: 0, charIndex: 0, charCount: 5, rects: [{ origin: { x: 1, y: 2 }, size: { width: 3, height: 4 } }], context: {} }], total: 1 }) as any,
        );

        await component['performSearch']('hello');

        expect(engineService.engine.searchAllPages).toHaveBeenCalledWith(expect.anything(), 'hello');
        expect(component['searchMatchesCount']()).toEqual({ current: 1, total: 1 });
    });

    it('should clear the search results', async () => {
        await loadPdf();
        engineService.engine.searchAllPages.mockReturnValue(task({ results: [{ pageIndex: 0, charIndex: 0, charCount: 1, rects: [], context: {} }], total: 1 }) as any);
        await component['performSearch']('x');
        expect(component['searchMatchesCount']()).toBeDefined();

        component['clearSearch']();

        expect(component['searchQuery']()).toBe('');
        expect(component['searchMatchesCount']()).toBeUndefined();
    });

    it('should re-render the on-screen pages when zooming in', async () => {
        await loadPdf();
        engineService.engine.renderPageRaw.mockClear();

        component['zoomIn']();
        await flushAsync();
        fixture.detectChanges();
        await flushAsync();

        expect(engineService.engine.renderPageRaw).toHaveBeenCalled();
    });

    it('should toggle fullscreen and emit the change', async () => {
        const emitted = vi.fn();
        component.isFullscreenChange.subscribe(emitted);
        await loadPdf();

        component.openFullscreen();
        expect(component['isFullscreen']()).toBe(true);
        expect(emitted).toHaveBeenLastCalledWith(true);

        component.closeFullscreen();
        expect(component['isFullscreen']()).toBe(false);
        expect(emitted).toHaveBeenLastCalledWith(false);
    });

    it('should emit downloadRequested when the download button is triggered', () => {
        const emitted = vi.fn();
        component.downloadRequested.subscribe(emitted);

        component['triggerDownload']();

        expect(emitted).toHaveBeenCalledOnce();
    });

    it('should navigate to the next and previous search matches with wraparound', async () => {
        await loadPdf(2);
        engineService.engine.searchAllPages.mockReturnValue(
            task({
                results: [
                    { pageIndex: 0, charIndex: 0, charCount: 1, rects: [], context: {} },
                    { pageIndex: 1, charIndex: 0, charCount: 1, rects: [], context: {} },
                ],
                total: 2,
            }) as any,
        );
        await component['performSearch']('a');
        expect(component['searchMatchesCount']()).toEqual({ current: 1, total: 2 });

        component['search'](false); // next
        expect(component['searchMatchesCount']()).toEqual({ current: 2, total: 2 });
        component['search'](false); // wraps to first
        expect(component['searchMatchesCount']()).toEqual({ current: 1, total: 2 });
        component['search'](true); // previous wraps to last
        expect(component['searchMatchesCount']()).toEqual({ current: 2, total: 2 });
    });

    it('should discard a stale search whose result resolves after it was cleared', async () => {
        await loadPdf();
        let resolveSearch!: (value: unknown) => void;
        engineService.engine.searchAllPages.mockReturnValue({
            toPromise: () => new Promise((resolve) => (resolveSearch = resolve)),
            wait: () => {},
            abort: () => {},
        } as any);

        const pending = component['performSearch']('slow');
        component['clearSearch']();
        // Let performSearch advance past `await getEngine()` to the searchAllPages call (which captures the resolver),
        // then resolve it: the result must be discarded because clearSearch already advanced the search token.
        await new Promise<void>((resolve) => setTimeout(resolve, 0));
        resolveSearch({ results: [{ pageIndex: 0, charIndex: 0, charCount: 1, rects: [], context: {} }], total: 1 });
        await pending;

        expect(component['searchMatchesCount']()).toBeUndefined();
        expect(component['searchQuery']()).toBe('');
    });

    it('should navigate pages, emit the change, and reject out-of-range input', async () => {
        await loadPdf(3);
        const emitted = vi.fn();
        component.currentPageChange.subscribe(emitted);

        component.goToPage(2);
        expect(component.getCurrentPage()).toBe(2);
        expect(emitted).toHaveBeenCalledWith(2);
        component.goToPage(99); // out of range -> ignored
        expect(component.getCurrentPage()).toBe(2);

        // An invalid page-input value resets to the current page.
        component['pageInputValue'].set(42);
        component['confirmPageNavigation']();
        expect(component['pageInputValue']()).toBe(2);
    });

    it('should emit currentPageChange when the current page changes', async () => {
        await loadPdf(3);
        const emitted = vi.fn();
        component.currentPageChange.subscribe(emitted);

        component['setCurrentPage'](3);

        expect(emitted).toHaveBeenCalledWith(3);
        expect(component.getCurrentPage()).toBe(3);
    });

    it('should clamp zoom-out at the minimum scale', async () => {
        await loadPdf();
        for (let i = 0; i < 20; i++) {
            component['zoomOut']();
        }
        await flushAsync();

        expect(component['scale']()).toBeGreaterThanOrEqual(0.2);
    });

    it('should close the old document and render the new one when the PDF URL changes', async () => {
        await loadPdf(3);
        engineService.engine.closeDocument.mockClear();
        engineService.engine.renderPageRaw.mockClear();

        const SECOND_URL = 'https://example.com/other.pdf';
        engineService.engine.openDocumentBuffer.mockReturnValue(task(createMockPdfDocument('doc2', 2)) as any);
        fixture.componentRef.setInput('pdfUrl', SECOND_URL);
        fixture.detectChanges();
        httpMock.expectOne(SECOND_URL).flush(new Blob(['%PDF'], { type: 'application/pdf' }));
        await flushAsync();
        fixture.detectChanges();

        expect(engineService.engine.closeDocument).toHaveBeenCalled();
        expect(engineService.engine.renderPageRaw).toHaveBeenCalled();
        expect(component['totalPages']()).toBe(2);
        expect(component['renderedPages']().length).toBe(2);
    });

    it('should expose external and internal link overlays for a rendered page', async () => {
        engineService.engine.getPageAnnotations.mockReturnValue(
            task([
                {
                    type: PdfAnnotationSubtype.LINK,
                    rect: { origin: { x: 10, y: 20 }, size: { width: 30, height: 12 } },
                    target: { type: 'action', action: { type: PdfActionType.URI, uri: 'https://example.com' } },
                },
                {
                    type: PdfAnnotationSubtype.LINK,
                    rect: { origin: { x: 5, y: 5 }, size: { width: 10, height: 10 } },
                    target: { type: 'destination', destination: { pageIndex: 4 } },
                },
            ]) as any,
        );

        await loadPdf(3);
        await flushAsync();

        const links = component['pageLinks']().get(0);
        expect(links).toEqual([expect.objectContaining({ url: 'https://example.com', targetPage: undefined }), expect.objectContaining({ url: undefined, targetPage: 5 })]);
    });

    it('should close fullscreen on escape', async () => {
        await loadPdf();
        component.openFullscreen();
        expect(component['isFullscreen']()).toBe(true);

        component.onFullscreenEscape({ preventDefault: vi.fn(), stopPropagation: vi.fn() } as unknown as Event);

        expect(component['isFullscreen']()).toBe(false);
    });

    it('should close the engine document on destroy', async () => {
        await loadPdf();

        fixture.destroy();
        await new Promise<void>((resolve) => setTimeout(resolve, 0));

        expect(engineService.engine.closeDocument).toHaveBeenCalled();
    });

    it('should resolve every supported link target type and ignore unsupported ones', () => {
        // No target at all.
        expect(component['resolveLinkTarget'](undefined)).toBeUndefined();
        // Direct destination -> 1-based target page.
        expect(component['resolveLinkTarget']({ type: 'destination', destination: { pageIndex: 2 } } as any)).toEqual({ targetPage: 3 });
        // URI action -> external url.
        expect(component['resolveLinkTarget']({ type: 'action', action: { type: PdfActionType.URI, uri: 'https://x.test' } } as any)).toEqual({ url: 'https://x.test' });
        // Goto / RemoteGoto actions -> internal 1-based target page.
        expect(component['resolveLinkTarget']({ type: 'action', action: { type: PdfActionType.Goto, destination: { pageIndex: 0 } } } as any)).toEqual({ targetPage: 1 });
        expect(component['resolveLinkTarget']({ type: 'action', action: { type: PdfActionType.RemoteGoto, destination: { pageIndex: 6 } } } as any)).toEqual({ targetPage: 7 });
        // An unsupported action type resolves to nothing (no overlay is created for it).
        expect(component['resolveLinkTarget']({ type: 'action', action: { type: PdfActionType.LaunchAppOrOpenFile, path: '/tmp/x' } } as any)).toBeUndefined();
    });

    it('should build per-page highlights (in PDF points) from search results and mark the active one', async () => {
        await loadPdf(2);
        engineService.engine.searchAllPages.mockReturnValue(
            task({
                results: [
                    { pageIndex: 0, charIndex: 0, charCount: 1, rects: [{ origin: { x: 1, y: 2 }, size: { width: 3, height: 4 } }], context: {} },
                    { pageIndex: 1, charIndex: 0, charCount: 1, rects: [{ origin: { x: 5, y: 6 }, size: { width: 7, height: 8 } }], context: {} },
                ],
                total: 2,
            }) as any,
        );

        await component['performSearch']('a');

        const highlights = component['pageHighlights']();
        expect(highlights.get(0)).toEqual([{ rect: { origin: { x: 1, y: 2 }, size: { width: 3, height: 4 } }, active: true }]);
        expect(highlights.get(1)).toEqual([{ rect: { origin: { x: 5, y: 6 }, size: { width: 7, height: 8 } }, active: false }]);

        // Advancing to the next match moves the active flag to the second result.
        component['search'](false);
        const afterNext = component['pageHighlights']();
        expect(afterNext.get(0)![0].active).toBe(false);
        expect(afterNext.get(1)![0].active).toBe(true);
    });

    it('should clear results (not run the engine) when searching for a blank query', async () => {
        await loadPdf();
        engineService.engine.searchAllPages.mockClear();

        await component['performSearch']('   ');

        expect(engineService.engine.searchAllPages).not.toHaveBeenCalled();
        expect(component['searchQuery']()).toBe('   ');
        expect(component['searchMatchesCount']()).toBeUndefined();
    });

    it('should skip non-link annotations and not re-fetch links for an already-loaded page', async () => {
        engineService.engine.getPageAnnotations.mockReturnValue(
            task([
                { type: PdfAnnotationSubtype.HIGHLIGHT, rect: { origin: { x: 0, y: 0 }, size: { width: 1, height: 1 } } },
                {
                    type: PdfAnnotationSubtype.LINK,
                    rect: { origin: { x: 10, y: 20 }, size: { width: 30, height: 12 } },
                    target: { type: 'action', action: { type: PdfActionType.URI, uri: 'https://only-link.test' } },
                },
            ]) as any,
        );

        await loadPdf(2);
        await flushAsync();

        // Only the LINK annotation produced an overlay; the highlight annotation was skipped.
        expect(component['pageLinks']().get(0)).toEqual([expect.objectContaining({ url: 'https://only-link.test' })]);

        // A second load of the same page must not re-query annotations (guarded by pageLinks().has(index)).
        engineService.engine.getPageAnnotations.mockClear();
        const doc = component['doc']()!;
        await component['loadPageLinks'](0, doc, doc.pages[0], component['renderToken']);
        expect(engineService.engine.getPageAnnotations).not.toHaveBeenCalled();
    });

    it('should render/clear pages and update the current page as the observers fire', async () => {
        await loadPdf(5);
        // observePages() runs on load; the two most recent observers are the render + current-page observers.
        expect(observers.length).toBeGreaterThanOrEqual(2);
        const renderObserver = observers[observers.length - 2];
        const currentPageObserver = observers[observers.length - 1];
        const pageEls = component['pageElements']().map((ref) => ref.nativeElement);
        const emitted = vi.fn();
        component.currentPageChange.subscribe(emitted);
        engineService.engine.renderPageRaw.mockClear();

        // Page index 4 (beyond the eager-rendered first three) scrolls into view -> becomes visible and renders.
        renderObserver.callback([entryFor(pageEls[4], 1)], renderObserver as unknown as IntersectionObserver);
        await flushAsync();
        expect(component['visibleIndices'].has(4)).toBe(true);
        expect(component['renderedIndices'].has(4)).toBe(true);
        expect(engineService.engine.renderPageRaw).toHaveBeenCalled();

        // The current-page observer picks the highest-ratio page (index 4 -> page 5) and emits the change.
        currentPageObserver.callback([entryFor(pageEls[0], 0.1), entryFor(pageEls[4], 0.9)], currentPageObserver as unknown as IntersectionObserver);
        expect(component.getCurrentPage()).toBe(5);
        expect(emitted).toHaveBeenLastCalledWith(5);

        // Scrolling page 4 out of view removes it from the visible set and releases its canvas.
        renderObserver.callback([entryFor(pageEls[4], 0)], renderObserver as unknown as IntersectionObserver);
        expect(component['visibleIndices'].has(4)).toBe(false);
        expect(component['renderedIndices'].has(4)).toBe(false);
    });

    it('should suppress observer-driven page changes while a programmatic scroll is in flight', async () => {
        await loadPdf(3);
        const currentPageObserver = observers[observers.length - 1];
        const pageEls = component['pageElements']().map((ref) => ref.nativeElement);
        const emitted = vi.fn();
        component.currentPageChange.subscribe(emitted);

        // goToPage sets programmaticScrollUntil ~700ms into the future; an observer callback during that window
        // must not emit currentPageChange for an intermediate page.
        component.goToPage(2);
        emitted.mockClear();
        currentPageObserver.callback([entryFor(pageEls[2], 1)], currentPageObserver as unknown as IntersectionObserver);
        expect(emitted).not.toHaveBeenCalled();
        expect(component.getCurrentPage()).toBe(2);
    });

    it('should refit and re-render when fullscreen toggles', async () => {
        await loadPdf(2);
        // Give the container a width so computeFitWidthScale changes the scale on refit.
        const container = component['pagesContainer']()!.nativeElement;
        Object.defineProperty(container, 'clientWidth', { configurable: true, value: 1200 });
        const refitSpy = vi.spyOn(component as any, 'refitAndRender');

        component.openFullscreen();
        await flushAsync();
        fixture.detectChanges();
        await flushAsync();

        expect(refitSpy).toHaveBeenCalled();
        // fit-width scale = min(MAX, max(MIN, 1184/595)) ~= 1.99 for A4 pages.
        expect(component['scale']()).toBeGreaterThan(1);
    });

    it('should apply and reset the drawer z-index layering around fullscreen', async () => {
        // Place the component host inside a .layout-content element so applyFullscreenLayering finds it.
        const layout = document.createElement('div');
        layout.className = 'layout-content';
        layout.style.zIndex = '10';
        const host = component['hostElementRef'].nativeElement as HTMLElement;
        host.parentElement?.removeChild(host);
        layout.appendChild(host);
        document.body.appendChild(layout);

        await loadPdf();
        component.openFullscreen();
        expect(layout.style.zIndex).toBe('4000');

        component.closeFullscreen();
        // The original z-index is restored on close.
        expect(layout.style.zIndex).toBe('10');

        document.body.removeChild(layout);
    });

    it('should ignore fullscreen open/close/escape calls that are no-ops', async () => {
        // Without a pdfUrl, openFullscreen is a no-op.
        component.openFullscreen();
        expect(component['isFullscreen']()).toBe(false);

        await loadPdf();
        // Escape while not in fullscreen is ignored.
        component.onFullscreenEscape({ preventDefault: vi.fn(), stopPropagation: vi.fn() } as unknown as Event);
        expect(component['isFullscreen']()).toBe(false);
        // closeFullscreen while not in fullscreen is a no-op.
        const emitted = vi.fn();
        component.isFullscreenChange.subscribe(emitted);
        component.closeFullscreen();
        expect(emitted).not.toHaveBeenCalled();

        // requestFullscreen opens (delegates to openFullscreen); a second openFullscreen is a no-op.
        component['requestFullscreen']();
        expect(component['isFullscreen']()).toBe(true);
        expect(emitted).toHaveBeenCalledTimes(1);
        component.openFullscreen();
        expect(emitted).toHaveBeenCalledTimes(1);
    });

    it('should compress the toolbar until its content fits its width', async () => {
        await loadPdf();
        const toolbar = component['toolbarCenter']()!.nativeElement;
        // jsdom reports 0 for layout metrics; simulate an overflowing toolbar that fits once compacted.
        Object.defineProperty(toolbar, 'clientWidth', { configurable: true, value: 200 });
        let scrollWidth = 400;
        Object.defineProperty(toolbar, 'scrollWidth', { configurable: true, get: () => scrollWidth });
        const toggleSpy = vi.spyOn(toolbar.classList, 'toggle').mockImplementation(((cls: string, force?: boolean) => {
            // Once the first compact level is applied, pretend the content now fits.
            if (force && cls.endsWith('compact-1')) {
                scrollWidth = 150;
            }
            return !!force;
        }) as any);

        component['updateToolbarCompressionLevel']();

        // Level 1 was applied and, because scrollWidth then fit clientWidth, the loop stopped without adding more.
        expect(toggleSpy).toHaveBeenCalledWith('artemis-pdf-toolbar__center--compact-1', true);
        expect(toggleSpy).not.toHaveBeenCalledWith('artemis-pdf-toolbar__center--compact-2', true);
    });

    it('should skip toolbar compression when the toolbar has no width', async () => {
        await loadPdf();
        const toolbar = component['toolbarCenter']()!.nativeElement;
        Object.defineProperty(toolbar, 'clientWidth', { configurable: true, value: 0 });
        const toggleSpy = vi.spyOn(toolbar.classList, 'toggle');

        component['updateToolbarCompressionLevel']();

        expect(toggleSpy).not.toHaveBeenCalled();
    });

    it('should reset the page input to the current page when total pages is zero', () => {
        // No document loaded -> totalPages is 0 -> confirmPageNavigation is a no-op guard.
        component['pageInputValue'].set(5);
        component['confirmPageNavigation']();
        expect(component['pageInputValue']()).toBe(5);
    });

    it('should navigate to a valid page entered in the page input', async () => {
        await loadPdf(4);
        const goToSpy = vi.spyOn(component, 'goToPage');

        component['pageInputValue'].set(3);
        component['confirmPageNavigation']();

        expect(goToSpy).toHaveBeenCalledWith(3);
        expect(component.getCurrentPage()).toBe(3);
    });

    it('should ignore next/previous navigation when there are no search results', async () => {
        await loadPdf();
        const scrollSpy = vi.spyOn(component as any, 'scrollToActiveResult');

        component['search'](false);

        expect(scrollSpy).not.toHaveBeenCalled();
        expect(component['searchMatchesCount']()).toBeUndefined();
    });

    it('should handle the page-input enter and focus interactions', async () => {
        await loadPdf();
        const blur = vi.fn();
        const select = vi.fn();
        vi.spyOn(component, 'pageInputElement').mockReturnValue({ input: { nativeElement: { blur, select } } } as any);
        const preventDefault = vi.fn();

        component['onPageInputEnter']({ preventDefault } as unknown as Event);
        expect(preventDefault).toHaveBeenCalled();
        expect(blur).toHaveBeenCalled();

        component['onPageInputFocus']();
        await new Promise<void>((resolve) => setTimeout(resolve, 0));
        expect(select).toHaveBeenCalled();
    });

    it('should trigger a search from the search-input enter handler', async () => {
        await loadPdf(2);
        engineService.engine.searchAllPages.mockReturnValue(
            task({
                results: [
                    { pageIndex: 0, charIndex: 0, charCount: 1, rects: [], context: {} },
                    { pageIndex: 1, charIndex: 0, charCount: 1, rects: [], context: {} },
                ],
                total: 2,
            }) as any,
        );
        await component['performSearch']('a');
        expect(component['searchMatchesCount']()).toEqual({ current: 1, total: 2 });
        const searchSpy = vi.spyOn(component as any, 'search');

        component['onSearchInputEnter']({ preventDefault: vi.fn() } as unknown as Event);

        // Enter advances to the next match (findPrevious=false).
        expect(searchSpy).toHaveBeenCalledWith(false);
        expect(component['searchMatchesCount']()).toEqual({ current: 2, total: 2 });
    });

    it('should jump to the initial page after loading when it is greater than one', async () => {
        engineService.engine.openDocumentBuffer.mockReturnValue(task(createMockPdfDocument('doc', 5)) as any);
        fixture.componentRef.setInput('pdfUrl', PDF_URL);
        fixture.componentRef.setInput('initialPage', 3);
        fixture.detectChanges();
        httpMock.expectOne(PDF_URL).flush(new Blob(['%PDF-1.7'], { type: 'application/pdf' }));
        await flushAsync();
        fixture.detectChanges();
        await flushAsync();

        expect(component.getCurrentPage()).toBe(3);
    });

    it('should discard a load whose URL changed while it was in flight and close the stale document', async () => {
        // First (stale) load: openDocumentBuffer resolves only after we trigger a second load, so the token check
        // in loadPdf supersedes it and its just-opened document is closed instead of shown.
        let resolveFirst!: (doc: unknown) => void;
        engineService.engine.openDocumentBuffer.mockReturnValueOnce({
            toPromise: () => new Promise((resolve) => (resolveFirst = resolve)),
            wait: () => {},
            abort: () => {},
        } as any);
        fixture.componentRef.setInput('pdfUrl', PDF_URL);
        fixture.detectChanges();
        httpMock.expectOne(PDF_URL).flush(new Blob(['%PDF'], { type: 'application/pdf' }));
        await new Promise<void>((resolve) => setTimeout(resolve, 0));

        // Second load supersedes the first (bumps renderToken).
        const SECOND_URL = 'https://example.com/second.pdf';
        engineService.engine.openDocumentBuffer.mockReturnValueOnce(task(createMockPdfDocument('doc2', 2)) as any);
        fixture.componentRef.setInput('pdfUrl', SECOND_URL);
        fixture.detectChanges();
        httpMock.expectOne(SECOND_URL).flush(new Blob(['%PDF'], { type: 'application/pdf' }));
        await flushAsync();

        // Now let the stale first load resolve: its document must be closed, and the second doc must remain active.
        engineService.engine.closeDocument.mockClear();
        resolveFirst(createMockPdfDocument('stale', 9));
        await flushAsync();

        expect(engineService.engine.closeDocument).toHaveBeenCalled();
        expect(component['totalPages']()).toBe(2);
    });

    it('should not render a page whose canvas is absent or whose token is stale', async () => {
        await loadPdf(3);
        engineService.engine.renderPageRaw.mockClear();

        // Stale token -> immediate no-op return.
        await component['renderPage'](0, component['renderToken'] - 1);
        expect(engineService.engine.renderPageRaw).not.toHaveBeenCalled();

        // No canvas for a non-existent page index -> no-op return before touching the engine.
        await component['renderPage'](99, component['renderToken']);
        expect(engineService.engine.renderPageRaw).not.toHaveBeenCalled();
    });

    it('should fall back to scale 1 when the container has no available width', async () => {
        await loadPdf(2);
        // jsdom container clientWidth is 0 -> available width is negative -> computeFitWidthScale returns 1.
        const scale = component['computeFitWidthScale'](component['doc']()!);
        expect(scale).toBe(1);
    });
});
