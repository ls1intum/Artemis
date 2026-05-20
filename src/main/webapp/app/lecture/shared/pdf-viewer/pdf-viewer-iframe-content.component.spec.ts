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
    let translateService: MockTranslateService;

    beforeEach(async () => {
        mockEventBus = { dispatch: vi.fn() };
        mockPdfViewerApp = { eventBus: mockEventBus };

        await TestBed.configureTestingModule({
            imports: [PdfViewerIframeContentComponent],
            providers: [PDFNotificationService, { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        translateService = TestBed.inject(TranslateService) as unknown as MockTranslateService;
        translateService.use('en');
        fixture = TestBed.createComponent(PdfViewerIframeContentComponent);
        component = fixture.componentInstance;
        postMessageSpy = vi.spyOn(window.parent, 'postMessage');
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.useRealTimers();
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
        const useSpy = vi.spyOn(translateService, 'use');
        window.dispatchEvent(
            new MessageEvent('message', {
                data: { type: 'loadPDF', data: { url: 'doc.pdf', initialPage: 5, isDarkMode: true, languageKey: 'de' } },
                origin: window.location.origin,
                source: window,
            }),
        );
        fixture.detectChanges();
        expect(component.pdfUrl()).toBe('doc.pdf');
        expect(component.currentPage()).toBe(5);
        expect(component.isDarkMode()).toBe(true);
        expect(useSpy).toHaveBeenCalledWith('de');
    });

    it('should reset document-specific state when loadPDF URL changes', () => {
        component.totalPages.set(42);
        (component as any).searchQuery.set('needle');
        (component as any).searchMatchesCount.set({ current: 4, total: 10 });

        window.dispatchEvent(
            new MessageEvent('message', {
                data: { type: 'loadPDF', data: { url: 'new-doc.pdf', initialPage: 3 } },
                origin: window.location.origin,
                source: window,
            }),
        );

        expect(component.pdfUrl()).toBe('new-doc.pdf');
        expect(component.totalPages()).toBe(0);
        expect((component as any).searchQuery()).toBe('');
        expect((component as any).searchMatchesCount()).toBeUndefined();
        expect(component.currentPage()).toBe(3);
    });

    it('should handle themeChange message', () => {
        window.dispatchEvent(new MessageEvent('message', { data: { type: 'themeChange', data: { isDarkMode: true } }, origin: window.location.origin, source: window }));
        expect(component.isDarkMode()).toBe(true);
    });

    it('should handle languageChange message', () => {
        const useSpy = vi.spyOn(translateService, 'use');
        window.dispatchEvent(new MessageEvent('message', { data: { type: 'languageChange', data: { languageKey: 'de' } }, origin: window.location.origin, source: window }));
        expect(useSpy).toHaveBeenCalledWith('de');
    });

    it('should handle viewerModeChange message without reloading the PDF', () => {
        component.pdfUrl.set('doc.pdf');
        component.isFullscreenMode.set(false);

        window.dispatchEvent(
            new MessageEvent('message', {
                data: { type: 'viewerModeChange', data: { viewerMode: 'fullscreen' } },
                origin: window.location.origin,
                source: window,
            }),
        );

        expect(component.pdfUrl()).toBe('doc.pdf');
        expect(component.isFullscreenMode()).toBe(true);
    });

    it('should update currentPage and post pageChange message', () => {
        postMessageSpy.mockClear();
        component.onPageChange(10);
        expect(component.currentPage()).toBe(10);
        expect(postMessageSpy).toHaveBeenCalledWith({ type: 'pageChange', data: { page: 10 } }, window.location.origin);
    });

    it('should post pageRendered once per loadPDF cycle', () => {
        postMessageSpy.mockClear();
        component.pdfUrl.set('doc-a.pdf');

        component.onPageRendered();
        component.onPageRendered();

        expect(postMessageSpy).toHaveBeenCalledTimes(1);
        expect(postMessageSpy).toHaveBeenCalledWith({ type: 'pageRendered', data: { url: 'doc-a.pdf' } }, window.location.origin);

        window.dispatchEvent(
            new MessageEvent('message', {
                data: { type: 'loadPDF', data: { url: 'doc-b.pdf' } },
                origin: window.location.origin,
                source: window,
            }),
        );

        component.onPageRendered();

        expect(postMessageSpy).toHaveBeenCalledTimes(2);
        expect(postMessageSpy).toHaveBeenLastCalledWith({ type: 'pageRendered', data: { url: 'doc-b.pdf' } }, window.location.origin);
    });

    it('should update totalPages on pdfLoaded', () => {
        component.currentPage.set(99);
        component.onPdfLoaded({ pagesCount: 12 });

        expect(component.totalPages()).toBe(12);
        expect(component.currentPage()).toBe(1);
    });

    it('should post pdfLoadError on loading failure', () => {
        postMessageSpy.mockClear();
        component.onPdfLoadingFailed();
        expect(postMessageSpy).toHaveBeenCalledWith({ type: 'pdfLoadError', data: { url: '' } }, window.location.origin);
    });

    it('should dispatch zoom events', () => {
        const pdfNotificationService = TestBed.inject(PDFNotificationService);
        vi.spyOn(pdfNotificationService, 'onPDFJSInitSignal').mockReturnValue(mockPdfViewerApp as any);
        component.zoomIn();
        expect(mockEventBus.dispatch).toHaveBeenCalledWith('zoomin');
        component.zoomOut();
        expect(mockEventBus.dispatch).toHaveBeenCalledWith('zoomout');
    });

    it('should execute next search and focus next button when Enter is pressed in search input', async () => {
        const pdfNotificationService = TestBed.inject(PDFNotificationService);
        vi.spyOn(pdfNotificationService, 'onPDFJSInitSignal').mockReturnValue(mockPdfViewerApp as any);
        (component as any).searchQuery.set('needle');
        (component as any).searchMatchesCount.set({ current: 1, total: 3 });
        fixture.detectChanges();

        const searchInput = fixture.nativeElement.querySelector('.artemis-pdf-toolbar__search-input') as HTMLInputElement;
        const searchControlButtons = fixture.nativeElement.querySelectorAll('.artemis-pdf-toolbar__search-controls .artemis-pdf-toolbar__button') as NodeListOf<HTMLButtonElement>;
        const searchNextButton = searchControlButtons.item(1);
        const enterEvent = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });

        searchInput.dispatchEvent(enterEvent);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(enterEvent.defaultPrevented).toBe(true);
        expect(mockEventBus.dispatch).toHaveBeenCalledWith('find', {
            type: 'again',
            query: 'needle',
            caseSensitive: false,
            entireWord: false,
            highlightAll: true,
            findPrevious: false,
        });
        expect(document.activeElement).toBe(searchNextButton);
    });

    it('should handle Escape key in fullscreen mode', () => {
        component.isFullscreenMode.set(true);
        const event = new KeyboardEvent('keydown', { key: 'Escape', cancelable: true });
        window.dispatchEvent(event);
        expect(postMessageSpy).toHaveBeenCalledWith({ type: 'closeFullscreen', data: {} }, window.location.origin);
    });

    it('should clear and close search when Escape is pressed in search input', () => {
        const pdfNotificationService = TestBed.inject(PDFNotificationService);
        vi.spyOn(pdfNotificationService, 'onPDFJSInitSignal').mockReturnValue(mockPdfViewerApp as any);
        (component as any).performSearch('needle');
        fixture.detectChanges();

        const searchInput = fixture.nativeElement.querySelector('.artemis-pdf-toolbar__search-input') as HTMLInputElement;
        searchInput.focus();
        expect(fixture.nativeElement.querySelector('.artemis-pdf-toolbar__search-controls')).toBeTruthy();

        const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });
        window.dispatchEvent(event);
        fixture.detectChanges();

        expect(event.defaultPrevented).toBe(true);
        expect((component as any).searchQuery()).toBe('');
        expect(fixture.nativeElement.querySelector('.artemis-pdf-toolbar__search-controls')).toBeFalsy();
        expect(mockEventBus.dispatch).toHaveBeenLastCalledWith('find', {
            type: 'find',
            query: '',
            caseSensitive: false,
            entireWord: false,
            highlightAll: false,
            findPrevious: false,
        });
        expect(document.activeElement).not.toBe(searchInput);
    });

    it('should keep the full toolbar visible when search is focused without a query', () => {
        const toolbarCenter = fixture.nativeElement.querySelector('.artemis-pdf-toolbar__center') as HTMLElement;
        const searchInput = fixture.nativeElement.querySelector('.artemis-pdf-toolbar__search-input') as HTMLInputElement;

        searchInput.focus();
        fixture.detectChanges();
        expect(toolbarCenter.classList.contains('artemis-pdf-toolbar__center--search-active')).toBe(false);

        (component as any).performSearch('needle');
        fixture.detectChanges();
        expect(toolbarCenter.classList.contains('artemis-pdf-toolbar__center--search-active')).toBe(true);
    });

    it('should cancel page navigation input when Escape is pressed', () => {
        component.totalPages.set(10);
        (component as any).pageInputValue.set(6);
        fixture.detectChanges();

        const pageInput = fixture.nativeElement.querySelector('.artemis-pdf-toolbar__page-input') as HTMLInputElement;
        pageInput.focus();

        const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });
        window.dispatchEvent(event);
        fixture.detectChanges();

        expect(event.defaultPrevented).toBe(true);
        expect((component as any).pageInputValue()).toBe(1);
        expect(document.activeElement).not.toBe(pageInput);
    });

    it('should render close button in fullscreen mode and trigger close action', () => {
        component.isFullscreenMode.set(true);
        fixture.detectChanges();
        postMessageSpy.mockClear();

        const closeButton = fixture.nativeElement.querySelector('.artemis-pdf-toolbar__close-fullscreen-button') as HTMLButtonElement;
        expect(closeButton).toBeTruthy();

        closeButton.click();

        expect(postMessageSpy).toHaveBeenCalledWith({ type: 'closeFullscreen', data: {} }, window.location.origin);
    });

    it('should confirm valid page navigation', () => {
        component.totalPages.set(10);
        (component as any).pageInputValue.set(5);
        (component as any).confirmPageNavigation();
        expect(component.currentPage()).toBe(5);
        expect((component as any).pageInputValue()).toBe(5);
    });

    it('should reset to current page when page navigation target is invalid', () => {
        component.totalPages.set(10);
        component.currentPage.set(4);
        (component as any).pageInputValue.set(99);
        (component as any).confirmPageNavigation();

        expect(component.currentPage()).toBe(4);
        expect((component as any).pageInputValue()).toBe(4);
    });

    it('should blur page input when Enter is pressed', () => {
        component.totalPages.set(10);
        fixture.detectChanges();

        const pageInput = fixture.nativeElement.querySelector('.artemis-pdf-toolbar__page-input') as HTMLInputElement;
        const blurSpy = vi.spyOn(pageInput, 'blur');
        const event = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true });

        (component as any).onPageInputEnter(event);

        expect(event.defaultPrevented).toBe(true);
        expect(blurSpy).toHaveBeenCalledOnce();
    });

    it('should set fullscreen mode flag from loadPDF message', () => {
        window.dispatchEvent(
            new MessageEvent('message', {
                data: { type: 'loadPDF', data: { url: 'doc.pdf', viewerMode: 'fullscreen' } },
                origin: window.location.origin,
                source: window,
            }),
        );
        expect(component.isFullscreenMode()).toBe(true);
    });

    const getToolbarCompressionLevel = (toolbarCenter: HTMLElement) => {
        for (let level = 5; level >= 1; level -= 1) {
            if (toolbarCenter.classList.contains(`artemis-pdf-toolbar__center--compact-${level}`)) {
                return level;
            }
        }
        return 0;
    };

    const setToolbarMeasurements = (toolbarCenter: HTMLElement, clientWidth: number, widthsByCompressionLevel: number[]) => {
        Object.defineProperty(toolbarCenter, 'clientWidth', { configurable: true, get: () => clientWidth });
        Object.defineProperty(toolbarCenter, 'scrollWidth', {
            configurable: true,
            get: () => widthsByCompressionLevel[getToolbarCompressionLevel(toolbarCenter)],
        });
    };

    it('should first hide the dividers when the toolbar starts overflowing', () => {
        const toolbarCenter = fixture.nativeElement.querySelector('.artemis-pdf-toolbar__center') as HTMLElement;
        setToolbarMeasurements(toolbarCenter, 450, [500, 430, 390, 360, 340, 320]);

        (component as any).updateToolbarCompressionLevel();
        fixture.detectChanges();

        expect(getToolbarCompressionLevel(toolbarCenter)).toBe(1);
        expect(toolbarCenter.classList.contains('artemis-pdf-toolbar__center--compact-1')).toBe(true);
        expect(toolbarCenter.classList.contains('artemis-pdf-toolbar__center--compact-2')).toBe(false);
    });

    it('should progressively hide controls in the configured order (dividers -> download -> search -> zoom -> page navigation)', () => {
        const toolbarCenter = fixture.nativeElement.querySelector('.artemis-pdf-toolbar__center') as HTMLElement;
        setToolbarMeasurements(toolbarCenter, 400, [500, 430, 390, 360, 340, 320]);

        (component as any).updateToolbarCompressionLevel();
        expect(getToolbarCompressionLevel(toolbarCenter)).toBe(2);
        expect(toolbarCenter.classList.contains('artemis-pdf-toolbar__center--compact-2')).toBe(true);

        setToolbarMeasurements(toolbarCenter, 370, [500, 430, 390, 360, 340, 320]);
        (component as any).updateToolbarCompressionLevel();
        expect(getToolbarCompressionLevel(toolbarCenter)).toBe(3);
        expect(toolbarCenter.classList.contains('artemis-pdf-toolbar__center--compact-3')).toBe(true);

        setToolbarMeasurements(toolbarCenter, 350, [500, 430, 390, 360, 340, 320]);
        (component as any).updateToolbarCompressionLevel();
        expect(getToolbarCompressionLevel(toolbarCenter)).toBe(4);
        expect(toolbarCenter.classList.contains('artemis-pdf-toolbar__center--compact-4')).toBe(true);

        setToolbarMeasurements(toolbarCenter, 330, [500, 430, 390, 360, 340, 320]);
        (component as any).updateToolbarCompressionLevel();
        expect(getToolbarCompressionLevel(toolbarCenter)).toBe(5);
        expect(toolbarCenter.classList.contains('artemis-pdf-toolbar__center--compact-5')).toBe(true);
    });

    it('should restore all controls when enough width is available again', () => {
        const toolbarCenter = fixture.nativeElement.querySelector('.artemis-pdf-toolbar__center') as HTMLElement;
        setToolbarMeasurements(toolbarCenter, 330, [500, 430, 390, 360, 340, 320]);
        (component as any).updateToolbarCompressionLevel();
        expect(getToolbarCompressionLevel(toolbarCenter)).toBe(5);

        setToolbarMeasurements(toolbarCenter, 700, [500, 430, 390, 360, 340, 320]);
        (component as any).updateToolbarCompressionLevel();
        fixture.detectChanges();

        expect(getToolbarCompressionLevel(toolbarCenter)).toBe(0);
        expect(toolbarCenter.classList.contains('artemis-pdf-toolbar__center--compact-1')).toBe(false);
    });
});
