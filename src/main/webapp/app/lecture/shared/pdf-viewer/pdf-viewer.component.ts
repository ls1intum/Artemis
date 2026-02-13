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

    totalPages = signal<number>(0);
    isLoading = signal<boolean>(true);
    error = signal<string | undefined>(undefined);

    private pdfDocument: PDFDocumentProxy | undefined;
    private readonly translateService = inject(TranslateService);
    private viewInitialized = signal<boolean>(false);

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
        // Signal that view is ready
        this.viewInitialized.set(true);
    }

    ngOnDestroy(): void {
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
            // Render all pages
            for (let pageNum = 1; pageNum <= numPages; pageNum++) {
                await this.renderPage(pageNum, container);
            }

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

    private async renderPage(pageNum: number, container: HTMLDivElement): Promise<void> {
        if (!this.pdfDocument) {
            return;
        }

        try {
            const page = await this.pdfDocument.getPage(pageNum);

            // Calculate scale to fit container width
            const containerWidth = container.clientWidth || 800;
            const viewport = page.getViewport({ scale: 1 });
            const scale = containerWidth / viewport.width;
            const scaledViewport = page.getViewport({ scale });

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
