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
        // jsdom does not implement object URLs nor scrollIntoView, both used by the viewer.
        URL.createObjectURL = vi.fn(() => 'blob:mock');
        URL.revokeObjectURL = vi.fn();
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

    it('should fetch, open, and render every page of the PDF', async () => {
        await loadPdf(3);

        expect(engineService.engine.openDocumentBuffer).toHaveBeenCalledOnce();
        expect(engineService.engine.renderPage).toHaveBeenCalledTimes(3);
        expect(component.totalPages()).toBe(3);
        expect(component.renderedPages().length).toBe(3);
    });

    it('should emit pageRendered after a successful load', async () => {
        const emitted = vi.fn();
        component.pageRendered.subscribe(emitted);

        await loadPdf();

        expect(emitted).toHaveBeenCalledWith({ pdfUrl: PDF_URL });
        expect(component.isLoading()).toBe(false);
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

        await component.performSearch('hello');

        expect(engineService.engine.searchAllPages).toHaveBeenCalledWith(expect.anything(), 'hello');
        expect(component.searchMatchesCount()).toEqual({ current: 1, total: 1 });
    });

    it('should clear the search results', async () => {
        await loadPdf();
        engineService.engine.searchAllPages.mockReturnValue(task({ results: [{ pageIndex: 0, charIndex: 0, charCount: 1, rects: [], context: {} }], total: 1 }) as any);
        await component.performSearch('x');
        expect(component.searchMatchesCount()).toBeDefined();

        component.clearSearch();

        expect(component.searchQuery()).toBe('');
        expect(component.searchMatchesCount()).toBeUndefined();
    });

    it('should re-render the pages when zooming in', async () => {
        await loadPdf();
        engineService.engine.renderPage.mockClear();

        component.zoomIn();
        await flushAsync();

        expect(engineService.engine.renderPage).toHaveBeenCalledTimes(3);
    });

    it('should toggle fullscreen and emit the change', async () => {
        const emitted = vi.fn();
        component.isFullscreenChange.subscribe(emitted);
        await loadPdf();

        component.openFullscreen();
        expect(component.isFullscreen()).toBe(true);
        expect(emitted).toHaveBeenLastCalledWith(true);

        component.closeFullscreen();
        expect(component.isFullscreen()).toBe(false);
        expect(emitted).toHaveBeenLastCalledWith(false);
    });

    it('should emit downloadRequested when the download button is triggered', () => {
        const emitted = vi.fn();
        component.downloadRequested.subscribe(emitted);

        component['triggerDownload']();

        expect(emitted).toHaveBeenCalledOnce();
    });

    it('should close the engine document on destroy', async () => {
        await loadPdf();

        fixture.destroy();
        await new Promise<void>((resolve) => setTimeout(resolve, 0));

        expect(engineService.engine.closeDocument).toHaveBeenCalled();
    });
});
