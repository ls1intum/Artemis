import { AfterViewInit, Component, ElementRef, OnDestroy, effect, inject, input, signal, viewChild } from '@angular/core';
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
    isLoading = signal<boolean>(true);
    error = signal<string | undefined>(undefined);
    zoomLevel = signal<number>(1.0);

    private pdfDocument: PDFDocumentProxy | undefined;
    private readonly translateService = inject(TranslateService);
    private viewInitialized = signal<boolean>(false);
    private resizeTimeout: number | undefined;

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
    }

    ngOnDestroy(): void {
        window.removeEventListener('resize', this.handleResize);

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
        } catch (err) {
            this.error.set(this.translateService.instant('artemisApp.attachmentVideoUnit.pdfViewer.error'));
            this.isLoading.set(false);
        }
    }

    /**
     * Renders all pages of the PDF document to the container.
     * Each page is rendered once at load time with consistent scaling.
     */
    private async renderAllPages(): Promise<void> {
        if (!this.pdfDocument) {
            return;
        }

        const containerRef = this.pdfContainer();
        if (!containerRef) {
            return;
        }

        const container = containerRef.nativeElement;
        container.innerHTML = '';

        // Calculate targetWidth ONCE before rendering any pages
        const targetWidth = this.calculateTargetWidth();
        const numPages = this.pdfDocument.numPages;

        // Render all pages with the same targetWidth
        for (let pageNum = 1; pageNum <= numPages; pageNum++) {
            await this.renderPage(pageNum, container, targetWidth);
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
        }
    }

    zoomOut(): void {
        if (this.zoomLevel() > 0.5) {
            this.zoomLevel.set(Math.max(0.5, this.zoomLevel() - 0.25));
        }
    }

    resetZoom(): void {
        this.zoomLevel.set(1.0);
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
