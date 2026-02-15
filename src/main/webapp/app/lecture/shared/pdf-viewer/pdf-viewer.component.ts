import { AfterViewInit, Component, ElementRef, OnDestroy, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import * as PDFJS from 'pdfjs-dist/legacy/build/pdf.mjs';
import type { PDFDocumentProxy } from 'pdfjs-dist';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-pdf-viewer',
    standalone: true,
    imports: [CommonModule, FontAwesomeModule],
    templateUrl: './pdf-viewer.component.html',
    styleUrls: ['./pdf-viewer.component.scss'],
})
export class PdfViewerComponent implements AfterViewInit, OnDestroy {
    pdfUrl = input.required<string>();
    pdfContainer = viewChild<ElementRef<HTMLDivElement>>('pdfContainer');
    pdfViewerBox = viewChild<ElementRef<HTMLDivElement>>('pdfViewerBox');

    totalPages = signal<number>(0);
    currentPage = signal<number>(1);
    isLoading = signal<boolean>(true);
    error = signal<string | undefined>(undefined);
    zoomLevel = signal<number>(1.0);

    // Computed wrapper width: at least 100%, or more when zoomed in
    wrapperWidth = computed(() => Math.max(100, this.zoomLevel() * 100));

    private pdfDocument: PDFDocumentProxy | undefined;
    private readonly translateService = inject(TranslateService);
    private viewInitialized = signal<boolean>(false);
    private resizeTimeout: number | undefined;
    private isRendering = false;

    constructor() {
        // Use legacy build to avoid ES2025 Promise.try compatibility issues
        (PDFJS.GlobalWorkerOptions as any).workerSrc = '/content/scripts/pdf.worker.min.mjs';

        // Load PDF when URL changes and view is ready
        effect(() => {
            const url = this.pdfUrl();
            const viewReady = this.viewInitialized();

            if (url && viewReady) {
                this.loadPdf(url);
            }
        });
    }

    ngAfterViewInit(): void {
        this.viewInitialized.set(true);

        // Re-render PDF when window is resized
        window.addEventListener('resize', this.handleResize);

        // Track current page on scroll
        const viewerBox = this.pdfViewerBox()?.nativeElement;
        if (viewerBox) {
            viewerBox.addEventListener('scroll', this.updateCurrentPage);
        }
    }

    ngOnDestroy(): void {
        window.removeEventListener('resize', this.handleResize);

        const viewerBox = this.pdfViewerBox()?.nativeElement;
        if (viewerBox) {
            viewerBox.removeEventListener('scroll', this.updateCurrentPage);
        }

        if (this.resizeTimeout !== undefined) {
            clearTimeout(this.resizeTimeout);
        }

        if (this.pdfDocument) {
            this.pdfDocument.destroy();
            this.pdfDocument = undefined;
        }
    }

    private async loadPdf(url: string): Promise<void> {
        this.isLoading.set(true);
        this.error.set(undefined);

        try {
            const loadingTask = PDFJS.getDocument({ url });
            this.pdfDocument = await loadingTask.promise;

            const numPages = this.pdfDocument.numPages;
            this.totalPages.set(numPages);

            if (numPages === 0) {
                this.error.set(this.translateService.instant('artemisApp.attachmentVideoUnit.pdfViewer.noPages'));
                this.isLoading.set(false);
                return;
            }

            const containerRef = this.pdfContainer();
            if (!containerRef) {
                this.error.set(this.translateService.instant('artemisApp.attachmentVideoUnit.pdfViewer.error'));
                this.isLoading.set(false);
                return;
            }

            await this.renderAllPages();
            this.isLoading.set(false);

            // Initialize current page after rendering
            setTimeout(() => this.updateCurrentPage(), 50);
        } catch (err) {
            this.error.set(this.translateService.instant('artemisApp.attachmentVideoUnit.pdfViewer.error'));
            this.isLoading.set(false);
        }
    }

    /**
     * Renders all pages of the PDF document to the container.
     * Each page is rendered once at load time with consistent scaling.
     * Prevents concurrent rendering operations to avoid race conditions.
     */
    private async renderAllPages(): Promise<void> {
        // Prevent concurrent rendering operations
        if (this.isRendering) {
            return;
        }

        if (!this.pdfDocument) {
            return;
        }

        const containerRef = this.pdfContainer();
        if (!containerRef) {
            return;
        }

        this.isRendering = true;

        try {
            const container = containerRef.nativeElement;
            container.innerHTML = '';

            // Calculate targetWidth ONCE before rendering any pages
            const targetWidth = this.calculateTargetWidth();
            const numPages = this.pdfDocument.numPages;

            // Render all pages with the same targetWidth
            for (let pageNum = 1; pageNum <= numPages; pageNum++) {
                await this.renderPage(pageNum, container, targetWidth);
            }

            // Update current page after all pages are rendered
            setTimeout(() => this.updateCurrentPage(), 50);
        } finally {
            this.isRendering = false;
        }
    }

    /**
     * Calculates the target width for PDF pages based on container dimensions.
     * Uses full available width (accounting for 40px total padding).
     */
    private calculateTargetWidth(): number {
        const viewerBoxRef = this.pdfViewerBox();
        const boxWidth = viewerBoxRef?.nativeElement.clientWidth || 800;
        const availableWidth = boxWidth - 40; // Subtract container padding (20px left + 20px right)
        return availableWidth;
    }

    zoomIn(): void {
        if (this.zoomLevel() < 3.0) {
            this.zoomLevel.set(Math.min(3.0, this.zoomLevel() + 0.25));
            this.centerHorizontalScroll();
        }
    }

    zoomOut(): void {
        if (this.zoomLevel() > 0.5) {
            this.zoomLevel.set(Math.max(0.5, this.zoomLevel() - 0.25));
            this.centerHorizontalScroll();
        }
    }

    resetZoom(): void {
        this.zoomLevel.set(1.0);
        this.centerHorizontalScroll();
    }

    /**
     * Centers the horizontal scroll position after zooming.
     * This ensures the PDF remains centered horizontally when zoom changes.
     */
    private centerHorizontalScroll(): void {
        // Wait for the next tick to ensure DOM is updated with new zoom level
        setTimeout(() => {
            const container = this.pdfViewerBox()?.nativeElement;
            if (container) {
                // Calculate the horizontal center position
                const scrollCenter = (container.scrollWidth - container.clientWidth) / 2;
                container.scrollLeft = Math.max(0, scrollCenter);
            }
        }, 0);
    }

    private handleResize = (): void => {
        // Debounce: wait 300ms after last resize before re-rendering
        if (this.resizeTimeout !== undefined) {
            clearTimeout(this.resizeTimeout);
        }
        this.resizeTimeout = window.setTimeout(() => {
            if (this.pdfDocument && !this.isLoading() && !this.error()) {
                this.renderAllPages();
            }
        }, 300);
    };

    /**
     * Updates the current page number based on which page has the most visible area in the viewport.
     * Safe to call during rendering - will skip if pages are not yet fully rendered.
     */
    private updateCurrentPage = (): void => {
        // Skip update if currently rendering to avoid inconsistent state
        if (this.isRendering) {
            return;
        }

        const viewerBox = this.pdfViewerBox()?.nativeElement;
        const container = this.pdfContainer()?.nativeElement;

        if (!viewerBox || !container) {
            return;
        }

        const pages = container.querySelectorAll('.pdf-page');
        if (pages.length === 0) {
            return;
        }

        // Verify we have the expected number of pages before updating
        const expectedPages = this.totalPages();
        if (expectedPages > 0 && pages.length !== expectedPages) {
            // Pages are still being rendered, skip update
            return;
        }

        const viewerRect = viewerBox.getBoundingClientRect();
        let maxVisibleArea = 0;
        let visiblePageNum = 1;

        pages.forEach((page, index) => {
            const pageRect = page.getBoundingClientRect();

            // Calculate intersection height between page and viewer
            const intersectionTop = Math.max(pageRect.top, viewerRect.top);
            const intersectionBottom = Math.min(pageRect.bottom, viewerRect.bottom);
            const visibleHeight = Math.max(0, intersectionBottom - intersectionTop);

            // The page with the most visible area is the current page
            if (visibleHeight > maxVisibleArea) {
                maxVisibleArea = visibleHeight;
                visiblePageNum = index + 1;
            }
        });

        this.currentPage.set(visiblePageNum);
    };

    /**
     * Renders a single PDF page to a canvas element and appends it to the container.
     */
    private async renderPage(pageNum: number, container: HTMLDivElement, targetWidth: number): Promise<void> {
        if (!this.pdfDocument) {
            return;
        }

        try {
            const page = await this.pdfDocument.getPage(pageNum);

            // Calculate scale based on target width
            const viewport = page.getViewport({ scale: 1 });
            const scale = targetWidth / viewport.width;

            // Apply devicePixelRatio for sharp rendering on Retina displays
            const pixelRatio = window.devicePixelRatio || 1;
            const scaledViewport = page.getViewport({ scale: scale * pixelRatio });

            const canvas = document.createElement('canvas');
            const context = canvas.getContext('2d');

            if (!context) {
                throw new Error('Could not get 2D context');
            }

            // Set canvas size in physical pixels
            canvas.height = scaledViewport.height;
            canvas.width = scaledViewport.width;

            // Scale canvas back to CSS pixels for display
            canvas.style.width = `${scaledViewport.width / pixelRatio}px`;
            canvas.style.height = `${scaledViewport.height / pixelRatio}px`;

            const renderContext = {
                canvasContext: context,
                viewport: scaledViewport,
                canvas: canvas,
            };

            // Render the page BEFORE adding it to the DOM to prevent flickering
            await page.render(renderContext).promise;

            // Only add to DOM after rendering is complete
            const pageDiv = document.createElement('div');
            pageDiv.className = 'pdf-page';
            pageDiv.appendChild(canvas);
            container.appendChild(pageDiv);
        } catch {
            // Silently skip pages that fail to render
        }
    }
}
