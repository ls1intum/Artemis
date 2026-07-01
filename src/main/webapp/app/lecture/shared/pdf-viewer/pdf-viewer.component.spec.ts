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

    const PDF_URL = 'https://example.com/lecture.pdf';
    const task = <R>(value: R) => ({ toPromise: () => Promise.resolve(value), wait: (cb: (v: R) => void) => cb(value), abort: () => {} });

    beforeEach(async () => {
        // jsdom does not implement scrollIntoView (used by goToPage); canvas 2d is provided by vitest-canvas-mock.
        HTMLElement.prototype.scrollIntoView = vi.fn();

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
});
