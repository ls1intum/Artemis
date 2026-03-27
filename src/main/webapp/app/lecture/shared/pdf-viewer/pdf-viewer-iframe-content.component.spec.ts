import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PdfViewerIframeContentComponent } from './pdf-viewer-iframe-content.component';
import { PDFNotificationService } from 'ngx-extended-pdf-viewer';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

vi.mock('pdfjs-dist/legacy/build/pdf.mjs', () => ({
    __esModule: true,
    GlobalWorkerOptions: { workerSrc: '' },
    getDocument: vi.fn(() => ({ promise: Promise.resolve({ numPages: 0, getPage: vi.fn(), destroy: vi.fn() }) })),
}));

describe('PdfViewerIframeContentComponent', () => {
    setupTestBed({ zoneless: true });

    let component: PdfViewerIframeContentComponent;
    let fixture: ComponentFixture<PdfViewerIframeContentComponent>;
    let mockEventBus: { dispatch: ReturnType<typeof vi.fn> };
    let mockPdfViewerApp: { eventBus: typeof mockEventBus };
    let postMessageSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        mockEventBus = { dispatch: vi.fn() };
        mockPdfViewerApp = { eventBus: mockEventBus };

        await TestBed.configureTestingModule({
            imports: [PdfViewerIframeContentComponent],
            providers: [PDFNotificationService, { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfViewerIframeContentComponent);
        component = fixture.componentInstance;
        postMessageSpy = vi.spyOn(window.parent, 'postMessage');
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('should create and initialize with defaults', () => {
        expect(component).toBeTruthy();
        expect(component.pdfUrl()).toBe('');
        expect(component.isDarkMode()).toBe(false);
        expect(postMessageSpy).toHaveBeenCalledWith({ type: 'ready', data: {} }, window.location.origin);
    });

    it('should reject messages from different origin', () => {
        window.dispatchEvent(new MessageEvent('message', { data: { type: 'loadPDF', data: { url: 'test.pdf' } }, origin: 'https://evil.com' }));
        expect(component.pdfUrl()).toBe('');
    });

    it('should handle loadPDF message and update signals', () => {
        window.dispatchEvent(
            new MessageEvent('message', {
                data: { type: 'loadPDF', data: { url: 'doc.pdf', initialPage: 5, isDarkMode: true } },
                origin: window.location.origin,
            }),
        );
        fixture.detectChanges();
        expect(component.pdfUrl()).toBe('doc.pdf');
        expect(component.currentPage()).toBe(5);
        expect(component.isDarkMode()).toBe(true);
    });

    it('should update initialPage even when URL is unchanged', () => {
        window.dispatchEvent(
            new MessageEvent('message', {
                data: { type: 'loadPDF', data: { url: 'doc.pdf', initialPage: 2 } },
                origin: window.location.origin,
            }),
        );
        window.dispatchEvent(
            new MessageEvent('message', {
                data: { type: 'loadPDF', data: { url: 'doc.pdf', initialPage: 7 } },
                origin: window.location.origin,
            }),
        );

        expect(component.pdfUrl()).toBe('doc.pdf');
        expect(component.currentPage()).toBe(7);
    });

    it('should not reset page when URL is unchanged and initialPage is missing', () => {
        window.dispatchEvent(
            new MessageEvent('message', {
                data: { type: 'loadPDF', data: { url: 'doc.pdf', initialPage: 4 } },
                origin: window.location.origin,
            }),
        );
        window.dispatchEvent(
            new MessageEvent('message', {
                data: { type: 'loadPDF', data: { url: 'doc.pdf' } },
                origin: window.location.origin,
            }),
        );

        expect(component.currentPage()).toBe(4);
    });

    it('should use default initialPage if not provided', () => {
        window.dispatchEvent(new MessageEvent('message', { data: { type: 'loadPDF', data: { url: 'doc.pdf' } }, origin: window.location.origin }));
        expect(component.currentPage()).toBe(1);
    });

    it('should handle themeChange message', () => {
        window.dispatchEvent(new MessageEvent('message', { data: { type: 'themeChange', data: { isDarkMode: true } }, origin: window.location.origin }));
        expect(component.isDarkMode()).toBe(true);
    });

    it('should update currentPage and post pageChange message', () => {
        postMessageSpy.mockClear();
        component.onPageChange(10);
        expect(component.currentPage()).toBe(10);
        expect(postMessageSpy).toHaveBeenCalledWith({ type: 'pageChange', data: { page: 10 } }, window.location.origin);
    });

    it('should update totalPages and post pagesLoaded message', () => {
        postMessageSpy.mockClear();
        component.onPagesLoaded({ pagesCount: 42, source: {} });
        expect(component.totalPages()).toBe(42);
        expect(postMessageSpy).toHaveBeenCalledWith({ type: 'pagesLoaded', data: { pagesCount: 42 } }, window.location.origin);
    });

    it('should post pdfLoadError on loading failure', () => {
        postMessageSpy.mockClear();
        component.onPdfLoadingFailed();
        expect(postMessageSpy).toHaveBeenCalledWith({ type: 'pdfLoadError', data: {} }, window.location.origin);
    });

    it('should dispatch zoom events', () => {
        const pdfNotificationService = TestBed.inject(PDFNotificationService);
        vi.spyOn(pdfNotificationService, 'onPDFJSInitSignal').mockReturnValue(mockPdfViewerApp as any);
        component.zoomIn();
        expect(mockEventBus.dispatch).toHaveBeenCalledWith('zoomin');
        component.zoomOut();
        expect(mockEventBus.dispatch).toHaveBeenCalledWith('zoomout');
    });

    it('should handle missing pdfViewerApplication gracefully', () => {
        const pdfNotificationService = TestBed.inject(PDFNotificationService);
        vi.spyOn(pdfNotificationService, 'onPDFJSInitSignal').mockReturnValue(null as any);
        expect(() => component.zoomIn()).not.toThrow();
        expect(() => component.zoomOut()).not.toThrow();
    });

    it('should cleanup on destroy', () => {
        const removeSpy = vi.spyOn(window, 'removeEventListener');
        fixture.destroy();
        expect(removeSpy).toHaveBeenCalledWith('message', expect.any(Function));
    });
});
