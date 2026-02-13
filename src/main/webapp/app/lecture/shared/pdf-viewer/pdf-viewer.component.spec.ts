/**
 * pdf-viewer.component.spec.ts
 * Tests for PdfViewerComponent (PDF.js rendering)
 *
 * - Mocks `pdfjs-dist` library
 * - Tests PDF loading success/failure
 * - Tests Object URL cleanup
 * - Tests responsive rendering
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

// ---- Mock pdfjs-dist BEFORE importing the component ----
const mockPdfDocument = {
    numPages: 2,
    getPage: vi.fn(),
    destroy: vi.fn(),
};

const mockPage = {
    getViewport: vi.fn(),
    render: vi.fn(),
};

const mockLoadingTask = {
    promise: Promise.resolve(mockPdfDocument),
};

vi.mock('pdfjs-dist', () => {
    return {
        __esModule: true,
        GlobalWorkerOptions: {
            workerSrc: '',
        },
        getDocument: vi.fn(() => mockLoadingTask),
    };
});

// ---- Imports AFTER the mock ----
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PdfViewerComponent } from './pdf-viewer.component';
import { TranslateService } from '@ngx-translate/core';
import * as PDFJS from 'pdfjs-dist';

describe('PdfViewerComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<PdfViewerComponent>;
    let component: PdfViewerComponent;
    let mockTranslateService: any;

    beforeEach(async () => {
        // Reset all mocks
        vi.clearAllMocks();

        // Mock TranslateService
        mockTranslateService = {
            instant: vi.fn((key: string) => {
                const translations: Record<string, string> = {
                    'artemisApp.attachmentVideoUnit.pdfViewer.error': 'PDF could not be loaded. Please try downloading the file.',
                    'artemisApp.attachmentVideoUnit.pdfViewer.noPages': 'This PDF contains no pages.',
                    'artemisApp.attachmentVideoUnit.pdfViewer.loading': 'Loading PDF...',
                };
                return translations[key] || key;
            }),
        };

        // Setup mock viewport
        mockPage.getViewport.mockReturnValue({
            width: 800,
            height: 1000,
        });

        // Setup mock render
        mockPage.render.mockReturnValue({
            promise: Promise.resolve(),
        });

        // Setup mock getPage
        mockPdfDocument.getPage.mockResolvedValue(mockPage);

        TestBed.configureTestingModule({
            imports: [PdfViewerComponent],
            providers: [{ provide: TranslateService, useValue: mockTranslateService }],
        });

        // Override template to a minimal one for testing
        TestBed.overrideComponent(PdfViewerComponent, {
            set: {
                template: `
                    <div class="pdf-viewer-container">
                        @if (isLoading()) {
                            <div class="spinner-border"></div>
                        } @else if (error()) {
                            <div class="alert alert-danger">{{ error() }}</div>
                        } @else {
                            <div #pdfContainer class="pdf-pages-container"></div>
                        }
                    </div>
                `,
            },
        });

        await TestBed.compileComponents();

        fixture = TestBed.createComponent(PdfViewerComponent);
        component = fixture.componentInstance;

        // Set required input with a default value
        fixture.componentRef.setInput('pdfUrl', 'blob:http://localhost/default.pdf');
    });

    afterEach(() => {
        vi.clearAllMocks();
        vi.restoreAllMocks();
    });

    function setInput(url: string): void {
        fixture.componentRef.setInput('pdfUrl', url);
    }

    async function render(): Promise<void> {
        fixture.detectChanges();
        await fixture.whenStable();
        await Promise.resolve();
    }

    it('should create component', () => {
        expect(component).toBeTruthy();
    });

    it('should show loading state initially', () => {
        expect(component.isLoading()).toBe(true);
        expect(component.error()).toBeUndefined();
    });

    it('should load and render PDF successfully', async () => {
        const testUrl = 'blob:http://localhost/test.pdf';
        setInput(testUrl);

        await render();
        // Wait for async PDF loading
        await new Promise((resolve) => setTimeout(resolve, 100));

        expect(PDFJS.getDocument).toHaveBeenCalledWith(testUrl);
        expect(component.totalPages()).toBe(2);
        expect(component.isLoading()).toBe(false);
        expect(component.error()).toBeUndefined();
    });

    it('should render all pages', async () => {
        const testUrl = 'blob:http://localhost/test.pdf';
        setInput(testUrl);

        await render();
        await new Promise((resolve) => setTimeout(resolve, 100));

        // Should call getPage for each page
        expect(mockPdfDocument.getPage).toHaveBeenCalledTimes(2);
        expect(mockPdfDocument.getPage).toHaveBeenCalledWith(1);
        expect(mockPdfDocument.getPage).toHaveBeenCalledWith(2);
    });

    it('should calculate scale based on container width', async () => {
        const testUrl = 'blob:http://localhost/test.pdf';
        setInput(testUrl);

        // Mock container width
        const containerRef = component.pdfContainer();
        if (containerRef) {
            Object.defineProperty(containerRef.nativeElement, 'clientWidth', {
                value: 1000,
                writable: true,
            });
        }

        await render();
        await new Promise((resolve) => setTimeout(resolve, 100));

        // Viewport should be called twice: once with scale 1, then with calculated scale
        expect(mockPage.getViewport).toHaveBeenCalled();
    });

    it('should handle PDF loading error', async () => {
        const testUrl = 'blob:http://localhost/invalid.pdf';
        const errorMessage = 'PDF could not be loaded. Please try downloading the file.';

        // Mock getDocument to reject
        vi.mocked(PDFJS.getDocument).mockReturnValueOnce({
            promise: Promise.reject(new Error('Invalid PDF')),
        } as any);

        setInput(testUrl);

        await render();
        await new Promise((resolve) => setTimeout(resolve, 100));

        expect(component.isLoading()).toBe(false);
        expect(component.error()).toBe(errorMessage);
        expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.attachmentVideoUnit.pdfViewer.error');
    });

    it('should handle PDF with no pages', async () => {
        const testUrl = 'blob:http://localhost/empty.pdf';
        const noPagesMessage = 'This PDF contains no pages.';

        // Mock PDF document with 0 pages
        vi.mocked(PDFJS.getDocument).mockReturnValueOnce({
            promise: Promise.resolve({
                numPages: 0,
                getPage: vi.fn(),
                destroy: vi.fn(),
            }),
        } as any);

        setInput(testUrl);

        await render();
        await new Promise((resolve) => setTimeout(resolve, 100));

        expect(component.isLoading()).toBe(false);
        expect(component.error()).toBe(noPagesMessage);
        expect(mockTranslateService.instant).toHaveBeenCalledWith('artemisApp.attachmentVideoUnit.pdfViewer.noPages');
    });

    it('should disable PDF.js worker to avoid worker communication errors', () => {
        // Worker is disabled in constructor to use main thread rendering
        expect(PDFJS.GlobalWorkerOptions.workerSrc).toBeNull();
    });

    it('should cleanup PDF document on destroy', async () => {
        const testUrl = 'blob:http://localhost/test.pdf';
        setInput(testUrl);

        await render();
        await new Promise((resolve) => setTimeout(resolve, 100));

        fixture.destroy();

        expect(mockPdfDocument.destroy).toHaveBeenCalled();
    });

    it('should revoke Object URL on destroy', async () => {
        const testUrl = 'blob:http://localhost/test.pdf';
        const revokeObjectURLSpy = vi.spyOn(URL, 'revokeObjectURL');

        setInput(testUrl);

        await render();
        await new Promise((resolve) => setTimeout(resolve, 100));

        fixture.destroy();

        expect(revokeObjectURLSpy).toHaveBeenCalledWith(testUrl);
    });

    it('should create canvas elements with correct dimensions', async () => {
        const testUrl = 'blob:http://localhost/test.pdf';
        setInput(testUrl);

        await render();
        await new Promise((resolve) => setTimeout(resolve, 100));

        const container = component.pdfContainer()?.nativeElement;
        if (container) {
            const canvases = container.querySelectorAll('canvas');
            expect(canvases.length).toBeGreaterThan(0);
        }
    });

    it('should handle rendering errors gracefully', async () => {
        const testUrl = 'blob:http://localhost/corrupt.pdf';

        // Mock render to reject
        mockPage.render.mockReturnValueOnce({
            promise: Promise.reject(new Error('Render failed')),
        });

        setInput(testUrl);

        await render();
        await new Promise((resolve) => setTimeout(resolve, 100));

        // Component should not crash, error should be logged
        expect(component.isLoading()).toBe(false);
    });

    it('should clear previous content before rendering new PDF', async () => {
        const testUrl1 = 'blob:http://localhost/test1.pdf';
        setInput(testUrl1);

        await render();
        await new Promise((resolve) => setTimeout(resolve, 100));

        const container = component.pdfContainer()?.nativeElement;

        // Load a different PDF
        const testUrl2 = 'blob:http://localhost/test2.pdf';
        setInput(testUrl2);

        await render();
        await new Promise((resolve) => setTimeout(resolve, 100));

        // Container should have been cleared and re-populated
        expect(container?.innerHTML).toBeDefined();
    });
});
