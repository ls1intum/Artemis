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
    // Signal-based APIs (Angular 21)
    pdfUrl = input.required<string>();
    pdfContainer = viewChild<ElementRef<HTMLDivElement>>('pdfContainer');
    pdfViewerBox = viewChild<ElementRef<HTMLDivElement>>('pdfViewerBox');

    totalPages = signal<number>(0);
    isLoading = signal<boolean>(true);
    error = signal<string | undefined>(undefined);

    private pdfDocument: PDFDocumentProxy | undefined;
    private readonly translateService = inject(TranslateService);
    private viewInitialized = signal<boolean>(false);
    private resizeObserver: ResizeObserver | undefined;
    private isRendering = false;

    constructor() {
        // Using legacy build with proper worker configuration
        // Legacy build avoids ES2025 Promise.try issues
        (PDFJS.GlobalWorkerOptions as any).workerSrc = '/content/scripts/pdf.worker.min.mjs';

        // React to pdfUrl changes, but only after view is initialized
        effect(() => {
            const url = this.pdfUrl();
            const viewReady = this.viewInitialized();

            // eslint-disable-next-line no-undef
            console.log('[PDF Viewer] Effect triggered - url:', url, 'viewReady:', viewReady);

            if (url && viewReady) {
                // eslint-disable-next-line no-undef
                console.log('[PDF Viewer] Starting PDF load...');
                this.loadPdf(url);
            }
        });
    }

    ngAfterViewInit(): void {
        // eslint-disable-next-line no-undef
        console.log('[PDF Viewer] ngAfterViewInit called, container:', this.pdfContainer());

        // Set up ResizeObserver to re-render PDF when viewer box size changes
        const viewerBoxRef = this.pdfViewerBox();
        if (viewerBoxRef) {
            this.resizeObserver = new ResizeObserver(() => {
                // Only re-render if PDF is already loaded and not currently rendering
                if (this.pdfDocument && !this.isLoading() && !this.error() && !this.isRendering) {
                    // eslint-disable-next-line no-undef
                    console.log('[PDF Viewer] Box resized, re-rendering PDF...');
                    this.reRenderPdf();
                }
            });
            this.resizeObserver.observe(viewerBoxRef.nativeElement);
        }

        // Signal that view is ready
        this.viewInitialized.set(true);
    }

    ngOnDestroy(): void {
        // Cleanup ResizeObserver
        if (this.resizeObserver) {
            this.resizeObserver.disconnect();
            this.resizeObserver = undefined;
        }

        // Cleanup PDF document
        if (this.pdfDocument) {
            this.pdfDocument.destroy();
            this.pdfDocument = undefined;
        }

        // Revoke Object URL to prevent memory leak
        try {
            const url = this.pdfUrl();
            if (url) {
                URL.revokeObjectURL(url);
            }
        } catch {
            // pdfUrl might not be set if component is destroyed before initialization
        }
    }

    private async loadPdf(url: string): Promise<void> {
        // eslint-disable-next-line no-undef
        console.log('[PDF Viewer] loadPdf called with url:', url);
        this.isLoading.set(true);
        this.error.set(undefined);

        try {
            // Load PDF document with explicit options
            // eslint-disable-next-line no-undef
            console.log('[PDF Viewer] Calling PDFJS.getDocument...');
            const loadingTask = PDFJS.getDocument({
                url: url,
                verbosity: 5, // Enable debug logging
            });
            this.pdfDocument = await loadingTask.promise;

            // eslint-disable-next-line no-undef
            console.log('[PDF Viewer] PDF loaded successfully, pages:', this.pdfDocument.numPages);

            const numPages = this.pdfDocument.numPages;
            this.totalPages.set(numPages);

            if (numPages === 0) {
                // eslint-disable-next-line no-undef
                console.warn('[PDF Viewer] PDF has 0 pages');
                this.error.set(this.translateService.instant('artemisApp.attachmentVideoUnit.pdfViewer.noPages'));
                this.isLoading.set(false);
                return;
            }

            // Clear previous content
            const containerRef = this.pdfContainer();
            // eslint-disable-next-line no-undef
            console.log('[PDF Viewer] Container ref:', containerRef);
            if (!containerRef) {
                // eslint-disable-next-line no-undef
                console.error('[PDF Viewer] Container not available!');
                this.error.set(this.translateService.instant('artemisApp.attachmentVideoUnit.pdfViewer.error'));
                this.isLoading.set(false);
                return;
            }
            const container = containerRef.nativeElement;
            container.innerHTML = '';

            // eslint-disable-next-line no-undef
            console.log('[PDF Viewer] Rendering', numPages, 'pages...');

            // Use viewer box width (more stable) and account for padding
            const viewerBoxRef = this.pdfViewerBox();
            const boxWidth = viewerBoxRef?.nativeElement.clientWidth || 800;
            // Subtract padding from container (20px left + 20px right = 40px total)
            const availableWidth = boxWidth - 40;
            const targetWidth = availableWidth * 0.9; // 90% to ensure it fits
            // eslint-disable-next-line no-undef
            console.log('[PDF Viewer] boxWidth:', boxWidth, 'availableWidth:', availableWidth, 'targetWidth:', targetWidth);

            // Render all pages with same target width
            this.isRendering = true;
            for (let pageNum = 1; pageNum <= numPages; pageNum++) {
                await this.renderPage(pageNum, container, targetWidth);
            }
            this.isRendering = false;

            // eslint-disable-next-line no-undef
            console.log('[PDF Viewer] All pages rendered successfully');
            this.isLoading.set(false);
        } catch (err: any) {
            // eslint-disable-next-line no-undef
            console.error('[PDF Viewer] Error loading PDF:', err);
            // eslint-disable-next-line no-undef
            console.error('[PDF Viewer] Error details:', {
                name: err?.name,
                message: err?.message,
                stack: err?.stack,
            });
            this.error.set(this.translateService.instant('artemisApp.attachmentVideoUnit.pdfViewer.error'));
            this.isLoading.set(false);
        }
    }

    private async reRenderPdf(): Promise<void> {
        if (!this.pdfDocument || this.isRendering) {
            return;
        }

        this.isRendering = true;

        try {
            const containerRef = this.pdfContainer();
            if (!containerRef) {
                return;
            }
            const container = containerRef.nativeElement;

            // Clear previous content
            container.innerHTML = '';

            // Use viewer box width (more stable) and account for padding
            const viewerBoxRef = this.pdfViewerBox();
            const boxWidth = viewerBoxRef?.nativeElement.clientWidth || 800;
            // Subtract padding from container (20px left + 20px right = 40px total)
            const availableWidth = boxWidth - 40;
            const targetWidth = availableWidth * 0.9; // 90% to ensure it fits

            // Re-render all pages with new dimensions
            const numPages = this.pdfDocument.numPages;
            // eslint-disable-next-line no-undef
            console.log('[PDF Viewer] Re-rendering', numPages, 'pages - boxWidth:', boxWidth, 'availableWidth:', availableWidth, 'targetWidth:', targetWidth);
            for (let pageNum = 1; pageNum <= numPages; pageNum++) {
                await this.renderPage(pageNum, container, targetWidth);
            }
            // eslint-disable-next-line no-undef
            console.log('[PDF Viewer] Re-rendering complete!');
        } finally {
            this.isRendering = false;
        }
    }

    private async renderPage(pageNum: number, container: HTMLDivElement, targetWidth: number): Promise<void> {
        if (!this.pdfDocument) {
            return;
        }

        try {
            const page = await this.pdfDocument.getPage(pageNum);

            // Calculate scale based on the pre-calculated target width
            const viewport = page.getViewport({ scale: 1 });
            const scale = targetWidth / viewport.width;
            const scaledViewport = page.getViewport({ scale });

            // eslint-disable-next-line no-undef
            console.log('[PDF Viewer] Page', pageNum, '- targetWidth:', targetWidth, 'pdfWidth:', viewport.width, 'scale:', scale, 'scaledWidth:', scaledViewport.width);

            // Create canvas element
            const canvas = document.createElement('canvas');
            canvas.className = 'pdf-page';
            const context = canvas.getContext('2d');

            if (!context) {
                throw new Error('Could not get 2D context');
            }

            canvas.height = scaledViewport.height;
            canvas.width = scaledViewport.width;

            // Wrap canvas in a div for styling
            const pageDiv = document.createElement('div');
            pageDiv.className = 'pdf-page';
            pageDiv.appendChild(canvas);
            container.appendChild(pageDiv);

            // Render PDF page into canvas
            const renderContext = {
                canvasContext: context,
                viewport: scaledViewport,
                canvas: canvas,
            };

            await page.render(renderContext).promise;
        } catch (err) {
            // eslint-disable-next-line no-undef
            console.error(`Error rendering page ${pageNum}:`, err);
        }
    }
}
