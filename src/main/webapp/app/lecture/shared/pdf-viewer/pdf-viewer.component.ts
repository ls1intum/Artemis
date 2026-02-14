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

    private pdfDocument: PDFDocumentProxy | undefined;
    private readonly translateService = inject(TranslateService);
    private viewInitialized = signal<boolean>(false);
    private resizeObserver: ResizeObserver | undefined;

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
        // Set up ResizeObserver to re-render PDF when container size changes
        const viewerBoxRef = this.pdfViewerBox();
        if (viewerBoxRef) {
            this.resizeObserver = new ResizeObserver(() => {
                if (this.pdfDocument && !this.isLoading() && !this.error()) {
                    this.renderAllPages();
                }
            });
            this.resizeObserver.observe(viewerBoxRef.nativeElement);
        }

        this.viewInitialized.set(true);
    }

    ngOnDestroy(): void {
        if (this.resizeObserver) {
            this.resizeObserver.disconnect();
            this.resizeObserver = undefined;
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
     * Calculates appropriate scaling based on container width.
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

        const targetWidth = this.calculateTargetWidth();
        const numPages = this.pdfDocument.numPages;

        for (let pageNum = 1; pageNum <= numPages; pageNum++) {
            await this.renderPage(pageNum, container, targetWidth);
        }
    }

    /**
     * Calculates the target width for PDF pages based on container dimensions.
     * Accounts for container padding (40px total) and uses 90% of available width.
     */
    private calculateTargetWidth(): number {
        const viewerBoxRef = this.pdfViewerBox();
        const boxWidth = viewerBoxRef?.nativeElement.clientWidth || 800;
        const availableWidth = boxWidth - 40; // Subtract container padding
        return availableWidth * 0.9; // 90% to ensure it fits
    }

    /**
     * Renders a single PDF page to a canvas element and appends it to the container.
     */
    private async renderPage(pageNum: number, container: HTMLDivElement, targetWidth: number): Promise<void> {
        if (!this.pdfDocument) {
            return;
        }

        try {
            const page = await this.pdfDocument.getPage(pageNum);

            const viewport = page.getViewport({ scale: 1 });
            const scale = targetWidth / viewport.width;
            const scaledViewport = page.getViewport({ scale });

            const canvas = document.createElement('canvas');
            const context = canvas.getContext('2d');

            if (!context) {
                throw new Error('Could not get 2D context');
            }

            canvas.height = scaledViewport.height;
            canvas.width = scaledViewport.width;

            const pageDiv = document.createElement('div');
            pageDiv.className = 'pdf-page';
            pageDiv.appendChild(canvas);
            container.appendChild(pageDiv);

            const renderContext = {
                canvasContext: context,
                viewport: scaledViewport,
            };

            await page.render(renderContext).promise;
        } catch {
            // Silently skip pages that fail to render
        }
    }
}
