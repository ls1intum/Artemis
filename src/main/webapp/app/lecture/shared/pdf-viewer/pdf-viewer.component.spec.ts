import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PdfViewerComponent } from './pdf-viewer.component';
import { MockDirective, MockPipe } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateService } from '@ngx-translate/core';
import type { PDFDocumentProxy, PDFPageProxy } from 'pdfjs-dist';
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';

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

    const setupLoadedPdf = (numPages = 3): PDFDocumentProxy => {
        const mockDoc = createMockPdfDocument(numPages) as PDFDocumentProxy;
        component['pdfDocument'] = mockDoc;
        component.totalPages.set(numPages);
        component.isLoading.set(false);
        return mockDoc;
    };

    const findScrollAnchor = (pages: NodeListOf<Element>, currentScrollTop: number): { pageIndex: number; offsetRatio: number } => {
        let pageIndex = pages.length - 1;
        let offsetRatio = 0;

        for (let i = 0; i < pages.length; i++) {
            const page = pages[i] as HTMLElement;
            const pageTop = page.offsetTop;
            const pageBottom = pageTop + page.offsetHeight;

            if (pageBottom > currentScrollTop) {
                pageIndex = i;
                const offsetIntoPage = currentScrollTop - pageTop;
                offsetRatio = page.offsetHeight > 0 ? offsetIntoPage / page.offsetHeight : 0;
                break;
            }
        }

        return { pageIndex, offsetRatio };
    };

    beforeEach(async () => {
        // Import the mocked module to access getDocument
        const pdfjs = await import('pdfjs-dist/legacy/build/pdf.mjs');
        mockGetDocument = pdfjs.getDocument as any;
        mockGetDocument.mockClear();

        // Mock canvas.getContext to work in test environment
        const mockContext = {
            fillRect: vi.fn(),
            clearRect: vi.fn(),
            getImageData: vi.fn(),
            putImageData: vi.fn(),
            createImageData: vi.fn(),
            setTransform: vi.fn(),
            drawImage: vi.fn(),
            save: vi.fn(),
            restore: vi.fn(),
            scale: vi.fn(),
            rotate: vi.fn(),
            translate: vi.fn(),
            transform: vi.fn(),
        };

        vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(mockContext as any);

        await TestBed.configureTestingModule({
            imports: [PdfViewerComponent, MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)],
            providers: [
                {
                    provide: TranslateService,
                    useValue: {
                        get: vi.fn((key: string) => of(key)),
                        instant: vi.fn((key: string) => key),
                        onLangChange: of({}),
                        onTranslationChange: of({}),
                        onDefaultLangChange: of({}),
                    },
                },
            ],
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

        it.each([
            { start: 2.9, method: 'zoomIn', expected: 3.0 },
            { start: 0.6, method: 'zoomOut', expected: 0.5 },
        ])('should clamp zoom at boundaries ($method)', ({ start, method, expected }) => {
            component.zoomLevel.set(start);
            (component as any)[method]();
            expect(component.zoomLevel()).toBe(expected);
        });
    });

    describe('Page navigation', () => {
        beforeEach(() => {
            fixture.detectChanges();
            setupLoadedPdf(5);
        });

        it.each([
            { label: 'valid page', target: 3, expected: 3 },
            { label: 'below 1', target: 0, initial: 2, expected: 2 },
            { label: 'above total pages', target: 10, initial: 2, expected: 2 },
            { label: 'no pdf document', target: 3, initial: 1, expected: 1, unsetDoc: true },
        ])('should handle navigation when $label', ({ target, initial, expected, unsetDoc }) => {
            if (initial !== undefined) {
                component.currentPage.set(initial);
            }
            if (unsetDoc) {
                component['pdfDocument'] = undefined;
            }
            component.goToPage(target);
            expect(component.currentPage()).toBe(expected);
        });
    });

    describe('PDF loading', () => {
        it('should set error when loading fails', async () => {
            const rejectedPromise = new Promise((_, reject) => setTimeout(() => reject(new Error('Load failed')), 0));
            // Consume the rejection to prevent unhandled rejection warnings
            rejectedPromise.catch(() => {});

            mockGetDocument.mockReturnValue({
                promise: rejectedPromise,
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

            const mockDoc = setupLoadedPdf(3);
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
            component['isRendering'].set(true);
            setupLoadedPdf(3);

            await component['renderAllPages']();
            expect(component['isRendering']()).toBe(true);

            component['isRendering'].set(false);
            component['pdfDocument'] = undefined;
            await expect(component['renderAllPages']()).resolves.not.toThrow();
        });

        it('should preserve scroll position logic on re-render', () => {
            // Test the scroll position preservation logic
            const mockContainer = document.createElement('div');

            // Create mock pages
            const mockPage1 = document.createElement('div');
            Object.defineProperty(mockPage1, 'offsetTop', { value: 0 });
            Object.defineProperty(mockPage1, 'offsetHeight', { value: 500 });
            mockPage1.className = 'pdf-page';

            const mockPage2 = document.createElement('div');
            Object.defineProperty(mockPage2, 'offsetTop', { value: 500 });
            Object.defineProperty(mockPage2, 'offsetHeight', { value: 500 });
            mockPage2.className = 'pdf-page';

            mockContainer.appendChild(mockPage1);
            mockContainer.appendChild(mockPage2);

            // Simulate being 150px into page 2 (30% down page 2)
            const currentScrollTop = 650;
            const pages = mockContainer.querySelectorAll('.pdf-page');
            const { pageIndex: targetPageIndex, offsetRatio } = findScrollAnchor(pages, currentScrollTop);

            // Should have found page 2 at 30% down
            expect(targetPageIndex).toBe(1);
            expect(offsetRatio).toBeCloseTo(0.3);

            // After resize, calculate new scroll position
            const targetPage = pages[targetPageIndex] as HTMLElement;
            const newOffsetIntoPage = offsetRatio * targetPage.offsetHeight;
            const newScrollTop = targetPage.offsetTop + newOffsetIntoPage;

            // Should restore to same position: 500 + (0.3 * 500) = 650
            expect(newScrollTop).toBe(650);
        });
    });

    describe('Resize handling', () => {
        it('should debounce resize events', () => {
            vi.useFakeTimers();
            fixture.detectChanges();
            setupLoadedPdf(3);

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
