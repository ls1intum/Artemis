import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PdfViewerComponent } from './pdf-viewer.component';
import { MockDirective } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import type { PDFDocumentProxy, PDFPageProxy } from 'pdfjs-dist';
import dayjs from 'dayjs/esm';

// Mock pdfjs-dist
vi.mock('pdfjs-dist/legacy/build/pdf.mjs', () => {
    return {
        __esModule: true,
        GlobalWorkerOptions: {},
        getDocument: vi.fn(),
    };
});

describe('PdfViewerComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<PdfViewerComponent>;
    let component: PdfViewerComponent;
    let mockGetDocument: any;

    const createMockPdfDocument = (numPages: number): Partial<PDFDocumentProxy> => ({
        numPages,
        getPage: vi.fn().mockImplementation((pageNum: number) => {
            return Promise.resolve({
                getViewport: vi.fn().mockReturnValue({
                    width: 800,
                    height: 1000,
                }),
                render: vi.fn().mockReturnValue({
                    promise: Promise.resolve(),
                }),
            } as Partial<PDFPageProxy>);
        }),
        destroy: vi.fn(),
    });

    beforeEach(async () => {
        // Import the mocked module to access getDocument
        const pdfjs = await import('pdfjs-dist/legacy/build/pdf.mjs');
        mockGetDocument = pdfjs.getDocument as any;
        mockGetDocument.mockClear();

        await TestBed.configureTestingModule({
            imports: [PdfViewerComponent, MockDirective(TranslateDirective)],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfViewerComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('pdfUrl', 'blob:http://localhost/test.pdf');
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    describe('Component initialization', () => {
        it('should create component', () => {
            expect(component).toBeTruthy();
        });

        it('should have required inputs', () => {
            expect(component.pdfUrl()).toBe('blob:http://localhost/test.pdf');
        });

        it('should initialize with loading state', () => {
            expect(component.isLoading()).toBe(true);
            expect(component.error()).toBeUndefined();
            expect(component.totalPages()).toBe(0);
            expect(component.currentPage()).toBe(1);
            expect(component.zoomLevel()).toBe(1.0);
        });

        it('should have view child references', () => {
            fixture.detectChanges();
            expect(component.pdfContainer).toBeDefined();
            expect(component.pdfViewerBox).toBeDefined();
        });

        it('should accept optional inputs', () => {
            const uploadDate = dayjs();
            fixture.componentRef.setInput('uploadDate', uploadDate);
            fixture.componentRef.setInput('version', 2);
            fixture.componentRef.setInput('initialPage', 3);

            expect(component.uploadDate()).toBe(uploadDate);
            expect(component.version()).toBe(2);
            expect(component.initialPage()).toBe(3);
        });

        it('should cleanup on destroy', () => {
            fixture.detectChanges();
            expect(() => fixture.destroy()).not.toThrow();
        });
    });

    describe('Zoom functionality', () => {
        beforeEach(() => {
            fixture.detectChanges();
        });

        it('should zoom in, out and reset zoom level', () => {
            const initialZoom = component.zoomLevel();
            component.zoomIn();
            expect(component.zoomLevel()).toBe(initialZoom + 0.25);

            component.zoomOut();
            expect(component.zoomLevel()).toBe(initialZoom);

            component.zoomLevel.set(2.0);
            component.resetZoom();
            expect(component.zoomLevel()).toBe(1.0);
        });

        it('should clamp zoom at min and max boundaries', () => {
            component.zoomLevel.set(2.9);
            component.zoomIn();
            expect(component.zoomLevel()).toBe(3.0);

            component.zoomLevel.set(0.6);
            component.zoomOut();
            expect(component.zoomLevel()).toBe(0.5);
        });
    });

    describe('Page navigation', () => {
        beforeEach(() => {
            fixture.detectChanges();
            component.totalPages.set(5);
            component['pdfDocument'] = createMockPdfDocument(5) as PDFDocumentProxy;
        });

        it('should navigate to valid page', () => {
            component.goToPage(3);
            expect(component.currentPage()).toBe(3);
        });

        it('should not navigate to page below 1', () => {
            component.currentPage.set(2);
            component.goToPage(0);
            expect(component.currentPage()).toBe(2);
        });

        it('should not navigate to page above total pages', () => {
            component.currentPage.set(2);
            component.goToPage(10);
            expect(component.currentPage()).toBe(2);
        });

        it('should not navigate if pdf document is not loaded', () => {
            component['pdfDocument'] = undefined;
            component.currentPage.set(1);
            component.goToPage(3);
            expect(component.currentPage()).toBe(1);
        });
    });

    describe('PDF loading', () => {
        it('should set error when loading fails', async () => {
            mockGetDocument.mockReturnValue({
                promise: Promise.reject(new Error('Load failed')),
            });

            fixture.detectChanges();

            await vi.waitFor(() => {
                expect(component.error()).toBe('error');
                expect(component.isLoading()).toBe(false);
            });
        });

        it('should set error when PDF has 0 pages', async () => {
            const mockDoc = createMockPdfDocument(0);
            mockGetDocument.mockReturnValue({
                promise: Promise.resolve(mockDoc),
            });

            fixture.detectChanges();

            await vi.waitFor(() => {
                expect(component.error()).toBe('error');
                expect(component.isLoading()).toBe(false);
            });
        });

        it('should successfully load PDF with pages', async () => {
            const mockDoc = createMockPdfDocument(3);
            mockGetDocument.mockReturnValue({
                promise: Promise.resolve(mockDoc),
            });

            fixture.detectChanges();

            await vi.waitFor(() => {
                expect(component.totalPages()).toBe(3);
                expect(component.isLoading()).toBe(false);
                expect(component.error()).toBeUndefined();
            });
        });
    });

    describe('Event listeners', () => {
        it('should add and remove window resize listener', () => {
            const addEventListenerSpy = vi.spyOn(window, 'addEventListener');
            const removeEventListenerSpy = vi.spyOn(window, 'removeEventListener');

            fixture.detectChanges();
            expect(addEventListenerSpy).toHaveBeenCalledWith('resize', expect.any(Function));

            fixture.destroy();
            expect(removeEventListenerSpy).toHaveBeenCalledWith('resize', expect.any(Function));
        });

        it('should setup ResizeObserver on viewer box', () => {
            fixture.detectChanges();
            const viewerBox = component.pdfViewerBox()?.nativeElement;
            expect(viewerBox).toBeDefined();
        });
    });

    describe('Component cleanup', () => {
        it('should cleanup resources on destroy', () => {
            fixture.detectChanges();

            const mockDoc = createMockPdfDocument(3);
            component['pdfDocument'] = mockDoc as PDFDocumentProxy;
            const disconnectSpy = vi.fn();
            component['resizeObserver'] = { disconnect: disconnectSpy } as any;
            const clearTimeoutSpy = vi.spyOn(global, 'clearTimeout');
            component['resizeTimeout'] = 123;

            fixture.destroy();

            expect(mockDoc.destroy).toHaveBeenCalled();
            expect(disconnectSpy).toHaveBeenCalled();
            expect(clearTimeoutSpy).toHaveBeenCalledWith(123);
        });
    });

    describe('Edge cases and error handling', () => {
        it('should handle initial page navigation after PDF loads', async () => {
            const mockDoc = createMockPdfDocument(10);
            mockGetDocument.mockReturnValue({
                promise: Promise.resolve(mockDoc),
            });

            fixture.componentRef.setInput('initialPage', 5);
            fixture.detectChanges();

            await vi.waitFor(() => {
                expect(component.totalPages()).toBe(10);
            });
        });

        it('should skip rendering when already rendering or no pdf document', async () => {
            component['isRendering'] = true;
            component['pdfDocument'] = createMockPdfDocument(3) as PDFDocumentProxy;

            await component['renderAllPages']();
            expect(component['isRendering']).toBe(true);

            component['isRendering'] = false;
            component['pdfDocument'] = undefined;
            await expect(component['renderAllPages']()).resolves.not.toThrow();
        });

        it('should restore scroll position correctly', () => {
            const mockViewerBox = {
                scrollTop: 0,
                scrollLeft: 0,
                scrollHeight: 1000,
                scrollWidth: 800,
            } as HTMLDivElement;

            component['restoreScrollPosition'](mockViewerBox, 0.5, 0.25);

            expect(mockViewerBox.scrollTop).toBe(500);
            expect(mockViewerBox.scrollLeft).toBe(200);
        });
    });

    describe('Resize handling', () => {
        it('should debounce resize events', () => {
            vi.useFakeTimers();
            fixture.detectChanges();
            component['pdfDocument'] = createMockPdfDocument(3) as PDFDocumentProxy;
            component.isLoading.set(false);

            const renderSpy = vi.spyOn(component as any, 'renderAllPages');

            component['handleResize']();
            component['handleResize']();
            component['handleResize']();

            expect(renderSpy).not.toHaveBeenCalled();

            vi.advanceTimersByTime(300);

            expect(renderSpy).toHaveBeenCalledTimes(1);

            vi.useRealTimers();
        });

        it('should not render on resize when loading, error state, or no document', () => {
            fixture.detectChanges();
            const renderSpy = vi.spyOn(component as any, 'renderAllPages');

            component.isLoading.set(true);
            component['handleResize']();
            expect(renderSpy).not.toHaveBeenCalled();

            component.isLoading.set(false);
            component.error.set('error');
            component['handleResize']();
            expect(renderSpy).not.toHaveBeenCalled();

            component.error.set(undefined);
            component['pdfDocument'] = undefined;
            component['handleResize']();
            expect(renderSpy).not.toHaveBeenCalled();
        });
    });
});
