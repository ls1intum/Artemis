import { AfterViewInit, Component, ElementRef, OnDestroy, computed, effect, input, signal, viewChild } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import * as PDFJS from 'pdfjs-dist/legacy/build/pdf.mjs';
import type { PDFDocumentProxy } from 'pdfjs-dist';
import type { Dayjs } from 'dayjs/esm';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faExclamationTriangle, faRotateLeft, faSearchMinus, faSearchPlus } from '@fortawesome/free-solid-svg-icons';
import { ButtonModule } from 'primeng/button';

type PdfViewerAnchorState = {
    pageIndex: number;
    pdfX: number;
    pdfY: number;
};

@Component({
    selector: 'jhi-pdf-viewer',
    standalone: true,
    imports: [FontAwesomeModule, TranslateModule, ArtemisDatePipe, ArtemisTranslatePipe, TranslateDirective, ButtonModule],
    templateUrl: './pdf-viewer.component.html',
    styleUrls: ['./pdf-viewer.component.scss'],
})
export class PdfViewerComponent implements AfterViewInit, OnDestroy {
    private static readonly DOM_RENDER_DELAY_MS = 50;
    private static readonly PAGE_NAVIGATION_DELAY_MS = 300;
    private static readonly RESIZE_DEBOUNCE_MS = 300;
    private static readonly RESIZE_WIDTH_THRESHOLD_PX = 30;
    private static readonly MAX_ZOOM_LEVEL = 3.0;
    private static readonly MIN_ZOOM_LEVEL = 0.5;
    private static readonly ZOOM_INCREMENT = 0.25;
    private static readonly ZOOM_RETRY_DELAY_MS = 100;
    private static readonly PAGE_SCROLL_OFFSET_PX = 20;
    private static clamp(value: number, min: number, max: number): number {
        return Math.max(min, Math.min(max, value));
    }

    pdfUrl = input.required<string>();
    uploadDate = input<Dayjs | undefined>(undefined);
    version = input<number | undefined>(undefined);
    initialPage = input<number | undefined>(undefined);
    pdfContainer = viewChild<ElementRef<HTMLDivElement>>('pdfContainer');
    pdfViewerBox = viewChild<ElementRef<HTMLDivElement>>('pdfViewerBox');

    totalPages = signal<number>(0);
    currentPage = signal<number>(1);
    isLoading = signal<boolean>(true);
    error = signal<string | undefined>(undefined);
    zoomLevel = signal<number>(1.0);

    // Show toolbar when PDF is loaded, regardless of individual page errors
    showToolbar = computed(() => !this.isLoading() && this.totalPages() > 0);

    protected readonly faSearchMinus = faSearchMinus;
    protected readonly faSearchPlus = faSearchPlus;
    protected readonly faRotateLeft = faRotateLeft;
    protected readonly faExclamationTriangle = faExclamationTriangle;

    private pdfDocument: PDFDocumentProxy | undefined;
    private viewInitialized = signal<boolean>(false);
    private resizeTimeout: number | undefined;
    private pageNavTimeoutId: number | undefined;
    private zoomRetryTimeoutId: number | undefined;
    private isRendering = false;
    private resizeObserver: ResizeObserver | undefined;
    private lastObservedWidth = 0;
    private isZooming = false;

    constructor() {
        (PDFJS.GlobalWorkerOptions as any).workerSrc = '/content/scripts/pdf.worker.min.mjs';

        effect(() => {
            const url = this.pdfUrl();
            const viewReady = this.viewInitialized();

            if (url && viewReady) {
                this.loadPdf(url);
            }
        });

        effect(() => {
            const targetPage = this.initialPage();
            const loaded = !this.isLoading();
            const totalPages = this.totalPages();

            if (targetPage && loaded && totalPages > 0) {
                // Clear any previous timeout before scheduling a new one
                if (this.pageNavTimeoutId !== undefined) {
                    clearTimeout(this.pageNavTimeoutId);
                    this.pageNavTimeoutId = undefined;
                }

                // Clamp to valid page range
                const validPage = Math.max(1, Math.min(targetPage, totalPages));
                this.pageNavTimeoutId = window.setTimeout(() => this.goToPage(validPage), PdfViewerComponent.PAGE_NAVIGATION_DELAY_MS);
            }
        });
    }

    ngAfterViewInit(): void {
        this.viewInitialized.set(true);

        window.addEventListener('resize', this.handleResize);

        const viewerBox = this.pdfViewerBox()?.nativeElement;
        if (viewerBox) {
            viewerBox.addEventListener('scroll', this.updateCurrentPage);

            // Detect container size changes (e.g., sidebar collapse)
            this.lastObservedWidth = viewerBox.clientWidth;
            this.resizeObserver = new ResizeObserver((entries) => {
                for (const entry of entries) {
                    const newWidth = entry.contentRect.width;
                    const widthDiff = Math.abs(newWidth - this.lastObservedWidth);

                    // Skip if rendering, zooming, or width change < 30px (scrollbar threshold)
                    if (!this.isRendering && !this.isZooming && widthDiff > PdfViewerComponent.RESIZE_WIDTH_THRESHOLD_PX) {
                        this.lastObservedWidth = newWidth;
                        this.handleResize();
                    }
                }
            });
            this.resizeObserver.observe(viewerBox);
        }
    }

    ngOnDestroy(): void {
        window.removeEventListener('resize', this.handleResize);

        const viewerBox = this.pdfViewerBox()?.nativeElement;
        if (viewerBox) {
            viewerBox.removeEventListener('scroll', this.updateCurrentPage);
        }

        if (this.resizeObserver) {
            this.resizeObserver.disconnect();
        }

        if (this.resizeTimeout !== undefined) {
            clearTimeout(this.resizeTimeout);
        }

        if (this.pageNavTimeoutId !== undefined) {
            clearTimeout(this.pageNavTimeoutId);
            this.pageNavTimeoutId = undefined;
        }

        if (this.zoomRetryTimeoutId !== undefined) {
            clearTimeout(this.zoomRetryTimeoutId);
            this.zoomRetryTimeoutId = undefined;
        }

        if (this.pdfDocument) {
            this.pdfDocument.destroy();
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
                this.error.set('error');
                this.isLoading.set(false);
                return;
            }

            const containerRef = this.pdfContainer();
            if (!containerRef) {
                this.error.set('error');
                this.isLoading.set(false);
                return;
            }

            await this.renderAllPages();
            this.isLoading.set(false);
        } catch (err) {
            this.error.set('error');
            this.isLoading.set(false);
        }
    }

    private async renderAllPages(): Promise<void> {
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

        const viewerBox = this.pdfViewerBox()?.nativeElement;
        const container = containerRef.nativeElement;

        const anchor = viewerBox ? this.captureAnchorState(viewerBox, container) : undefined;

        this.isRendering = true;

        try {
            container.innerHTML = '';

            const targetWidth = this.calculateTargetWidth();
            const numPages = this.pdfDocument.numPages;

            let pagesSucceeded = 0;

            for (let pageNum = 1; pageNum <= numPages; pageNum++) {
                const success = await this.renderPage(pageNum, container, targetWidth);
                if (success) {
                    pagesSucceeded++;
                }
            }

            // If no pages rendered successfully, set error state
            if (pagesSucceeded === 0) {
                this.error.set('error');
                this.isRendering = false;
                return;
            }

            setTimeout(() => {
                if (this.zoomLevel() !== 1.0) {
                    this.applyZoomToPages();
                }

                // Restore scroll position to the same top-left PDF content
                if (viewerBox && anchor) {
                    this.restoreAnchorState(anchor, viewerBox, container);
                }

                this.updateCurrentPage();
            }, PdfViewerComponent.DOM_RENDER_DELAY_MS);
        } finally {
            this.isRendering = false;
        }
    }

    private calculateTargetWidth(): number {
        const viewerBoxRef = this.pdfViewerBox();
        return viewerBoxRef?.nativeElement.clientWidth || 800;
    }

    zoomIn(): void {
        if (this.zoomLevel() < PdfViewerComponent.MAX_ZOOM_LEVEL) {
            this.zoomLevel.set(Math.min(PdfViewerComponent.MAX_ZOOM_LEVEL, this.zoomLevel() + PdfViewerComponent.ZOOM_INCREMENT));
            this.performZoom();
        }
    }

    zoomOut(): void {
        if (this.zoomLevel() > PdfViewerComponent.MIN_ZOOM_LEVEL) {
            this.zoomLevel.set(Math.max(PdfViewerComponent.MIN_ZOOM_LEVEL, this.zoomLevel() - PdfViewerComponent.ZOOM_INCREMENT));
            this.performZoom();
        }
    }

    resetZoom(): void {
        this.zoomLevel.set(1.0);
        this.performZoom();
    }

    private performZoom(): void {
        // Wait for rendering to complete
        if (this.isRendering) {
            // Clear any previous retry timeout
            if (this.zoomRetryTimeoutId !== undefined) {
                clearTimeout(this.zoomRetryTimeoutId);
            }
            this.zoomRetryTimeoutId = window.setTimeout(() => this.performZoom(), PdfViewerComponent.ZOOM_RETRY_DELAY_MS);
            return;
        }

        // Clear retry timeout since we're proceeding with zoom
        if (this.zoomRetryTimeoutId !== undefined) {
            clearTimeout(this.zoomRetryTimeoutId);
            this.zoomRetryTimeoutId = undefined;
        }

        // Pause observer to prevent scrollbar feedback loop
        this.isZooming = true;

        const viewerBox = this.pdfViewerBox()?.nativeElement;
        const oldScrollLeft = viewerBox?.scrollLeft ?? 0;
        const oldScrollTop = viewerBox?.scrollTop ?? 0;
        const oldScrollWidth = viewerBox?.scrollWidth ?? 0;
        const oldScrollHeight = viewerBox?.scrollHeight ?? 0;
        const clientWidth = viewerBox?.clientWidth ?? 0;
        const clientHeight = viewerBox?.clientHeight ?? 0;

        // Calculate center point (horizontal) and top (vertical)
        const centerXRatio = oldScrollWidth > 0 ? (oldScrollLeft + clientWidth / 2) / oldScrollWidth : 0.5;
        const topYRatio = oldScrollHeight > 0 ? oldScrollTop / oldScrollHeight : 0;

        this.applyZoomToPages();

        requestAnimationFrame(() => {
            if (!viewerBox) {
                return;
            }

            const newScrollWidth = viewerBox.scrollWidth;
            const newScrollHeight = viewerBox.scrollHeight;

            let newScrollLeft = centerXRatio * newScrollWidth - clientWidth / 2;
            let newScrollTop = topYRatio * newScrollHeight;

            const maxScrollLeft = Math.max(0, newScrollWidth - clientWidth);
            const maxScrollTop = Math.max(0, newScrollHeight - clientHeight);

            newScrollLeft = Math.max(0, Math.min(maxScrollLeft, newScrollLeft));
            newScrollTop = Math.max(0, Math.min(maxScrollTop, newScrollTop));

            viewerBox.scrollLeft = newScrollLeft;
            viewerBox.scrollTop = newScrollTop;
        });

        // Resume observer after scrollbar changes settle
        setTimeout(() => {
            if (viewerBox) {
                this.lastObservedWidth = viewerBox.clientWidth;
            }

            this.isZooming = false;
        }, PdfViewerComponent.ZOOM_RETRY_DELAY_MS);
    }

    /** Navigates to the specified page (1-indexed). */
    goToPage(pageNumber: number): void {
        if (!this.pdfDocument || pageNumber < 1 || pageNumber > this.totalPages()) {
            return;
        }

        const container = this.pdfContainer()?.nativeElement;
        if (!container) {
            return;
        }

        const pageElements = container.querySelectorAll('.pdf-page');
        const targetPage = pageElements[pageNumber - 1];

        if (targetPage) {
            const viewerBox = this.pdfViewerBox()?.nativeElement;
            if (viewerBox) {
                const pageRect = (targetPage as HTMLElement).getBoundingClientRect();
                const containerRect = viewerBox.getBoundingClientRect();
                const offset = pageRect.top - containerRect.top + viewerBox.scrollTop;

                viewerBox.scrollTo({
                    top: offset - PdfViewerComponent.PAGE_SCROLL_OFFSET_PX,
                    behavior: 'smooth',
                });
            }
        }

        this.currentPage.set(pageNumber);
    }

    /** Adjusts page dimensions via CSS without re-rendering canvas pixel data. */
    private applyZoomToPages(): void {
        const container = this.pdfContainer()?.nativeElement;
        if (!container) {
            return;
        }

        const zoom = this.zoomLevel();
        const pages = container.querySelectorAll('.pdf-page');

        pages.forEach((pageElement) => {
            const page = pageElement as HTMLElement;
            const canvas = page.querySelector('canvas') as HTMLCanvasElement;

            if (canvas?.dataset.originalWidth && canvas.dataset.originalHeight) {
                const width = parseFloat(canvas.dataset.originalWidth) * zoom;
                const height = parseFloat(canvas.dataset.originalHeight) * zoom;
                canvas.style.width = `${width}px`;
                canvas.style.height = `${height}px`;
                page.style.width = canvas.style.width;
                page.style.height = canvas.style.height;
            }
        });
    }

    private getPageMetrics(
        page: HTMLElement,
        container: HTMLDivElement,
    ): {
        left: number;
        top: number;
        width: number;
        height: number;
        scaleX: number;
        scaleY: number;
        pdfWidth: number;
        pdfHeight: number;
    } {
        const rect = page.getBoundingClientRect();
        const containerRect = container.getBoundingClientRect();
        const left = rect.left - containerRect.left;
        const top = rect.top - containerRect.top;
        const width = rect.width;
        const height = rect.height;
        const pdfWidth = Number(page.dataset.pdfWidth) || 0;
        const pdfHeight = Number(page.dataset.pdfHeight) || 0;
        const scaleX = pdfWidth > 0 ? width / pdfWidth : 0;
        const scaleY = pdfHeight > 0 ? height / pdfHeight : 0;
        return {
            left,
            top,
            width,
            height,
            scaleX,
            scaleY,
            pdfWidth,
            pdfHeight,
        };
    }

    private captureAnchorState(viewerBox: HTMLDivElement, container: HTMLDivElement): PdfViewerAnchorState {
        const pages = container.querySelectorAll('.pdf-page');
        if (pages.length === 0) {
            return { pageIndex: 0, pdfX: 0, pdfY: 0 };
        }

        const anchorX = viewerBox.scrollLeft;
        const anchorY = viewerBox.scrollTop;

        let pageIndex = pages.length - 1;
        for (let i = 0; i < pages.length; i++) {
            const page = pages[i] as HTMLElement;
            const metrics = this.getPageMetrics(page, container);
            if (anchorY <= metrics.top + metrics.height) {
                pageIndex = i;
                break;
            }
        }

        const page = pages[pageIndex] as HTMLElement;
        const { left, top, scaleX, scaleY } = this.getPageMetrics(page, container);

        return {
            pageIndex,
            pdfX: scaleX ? (anchorX - left) / scaleX : 0,
            pdfY: scaleY ? (anchorY - top) / scaleY : 0,
        };
    }

    private restoreAnchorState(anchor: PdfViewerAnchorState, viewerBox: HTMLDivElement, container: HTMLDivElement): void {
        const pages = container.querySelectorAll('.pdf-page');
        if (pages.length === 0) {
            return;
        }

        const pageIndex = Math.min(anchor.pageIndex, pages.length - 1);
        const page = pages[pageIndex] as HTMLElement;
        const { left, top, scaleX, scaleY } = this.getPageMetrics(page, container);

        const desiredScrollLeft = left + (scaleX ? anchor.pdfX * scaleX : 0);
        const desiredScrollTop = top + (scaleY ? anchor.pdfY * scaleY : 0);
        const maxScrollLeft = Math.max(0, viewerBox.scrollWidth - viewerBox.clientWidth);
        const maxScrollTop = Math.max(0, viewerBox.scrollHeight - viewerBox.clientHeight);

        viewerBox.scrollLeft = PdfViewerComponent.clamp(desiredScrollLeft, 0, maxScrollLeft);
        viewerBox.scrollTop = PdfViewerComponent.clamp(desiredScrollTop, 0, maxScrollTop);
    }

    private handleResize = (): void => {
        if (this.resizeTimeout !== undefined) {
            clearTimeout(this.resizeTimeout);
        }
        this.resizeTimeout = window.setTimeout(() => {
            if (this.pdfDocument && !this.isLoading() && !this.error()) {
                this.renderAllPages();
            }
        }, PdfViewerComponent.RESIZE_DEBOUNCE_MS);
    };

    private updateCurrentPage = (): void => {
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

    private async renderPage(pageNum: number, container: HTMLDivElement, targetWidth: number): Promise<boolean> {
        if (!this.pdfDocument) {
            return false;
        }

        try {
            const page = await this.pdfDocument.getPage(pageNum);

            // Calculate scale based on target width
            const viewport = page.getViewport({ scale: 1 });
            const scale = targetWidth / viewport.width;

            // Use devicePixelRatio for sharp Retina rendering
            const pixelRatio = window.devicePixelRatio || 1;
            const scaledViewport = page.getViewport({ scale: scale * pixelRatio });

            const canvas = document.createElement('canvas');
            const context = canvas.getContext('2d');

            if (!context) {
                return false;
            }

            // Set canvas size in physical pixels
            canvas.height = scaledViewport.height;
            canvas.width = scaledViewport.width;

            // Scale canvas back to CSS pixels for display
            const cssWidth = scaledViewport.width / pixelRatio;
            const cssHeight = scaledViewport.height / pixelRatio;
            canvas.style.width = `${cssWidth}px`;
            canvas.style.height = `${cssHeight}px`;

            // Store original dimensions for zoom
            canvas.dataset.originalWidth = `${cssWidth}`;
            canvas.dataset.originalHeight = `${cssHeight}`;

            const renderContext = {
                canvasContext: context,
                viewport: scaledViewport,
                canvas: canvas,
            };

            // Render before DOM insertion to prevent flicker
            await page.render(renderContext).promise;

            // Only add to DOM after rendering is complete
            const pageDiv = document.createElement('div');
            pageDiv.className = 'pdf-page';
            pageDiv.style.width = canvas.style.width;
            pageDiv.style.height = canvas.style.height;
            pageDiv.dataset.pdfWidth = `${viewport.width}`;
            pageDiv.dataset.pdfHeight = `${viewport.height}`;
            pageDiv.appendChild(canvas);
            container.appendChild(pageDiv);

            return true;
        } catch (error) {
            return false;
        }
    }
}
