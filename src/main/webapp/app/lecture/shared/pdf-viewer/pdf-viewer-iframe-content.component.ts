import { Component, OnDestroy, OnInit, ViewEncapsulation, inject, signal } from '@angular/core';
import { NgxExtendedPdfViewerModule, PDFNotificationService, type PagesLoadedEvent, pdfDefaultOptions } from 'ngx-extended-pdf-viewer';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faMagnifyingGlassMinus, faMagnifyingGlassPlus } from '@fortawesome/free-solid-svg-icons';

pdfDefaultOptions.assetsFolder = 'assets/ngx-extended-pdf-viewer';

/**
 * Standalone PDF viewer component that runs inside an iframe.
 * This allows multiple instances of ngx-extended-pdf-viewer on the same page.
 */
@Component({
    selector: 'jhi-pdf-viewer-iframe-content',
    standalone: true,
    imports: [NgxExtendedPdfViewerModule, FaIconComponent],
    templateUrl: './pdf-viewer-iframe-content.component.html',
    styleUrls: ['./pdf-viewer-iframe-content.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class PdfViewerIframeContentComponent implements OnInit, OnDestroy {
    private readonly pdfNotificationService = inject(PDFNotificationService);

    readonly pdfUrl = signal<string>('');
    readonly initialPage = signal<number>(1);
    readonly currentPage = signal<number>(1);
    readonly totalPages = signal<number>(0);

    protected readonly faMagnifyingGlassMinus = faMagnifyingGlassMinus;
    protected readonly faMagnifyingGlassPlus = faMagnifyingGlassPlus;

    ngOnInit(): void {
        // Listen for messages from parent window
        window.addEventListener('message', this.handleParentMessage);

        // Notify parent that iframe is ready
        this.postMessageToParent('ready', {});
    }

    ngOnDestroy(): void {
        window.removeEventListener('message', this.handleParentMessage);
    }

    private readonly handleParentMessage = (event: MessageEvent): void => {
        // Validate message structure (guards against messages from other sources)
        if (!event.data || typeof event.data !== 'object') {
            return;
        }

        const { type, data } = event.data;

        switch (type) {
            case 'loadPDF':
                if (data.url) {
                    this.pdfUrl.set(data.url);
                    this.initialPage.set(data.initialPage || 1);
                }
                break;
        }
    };

    onPageChange(page: number): void {
        this.currentPage.set(page);
        this.postMessageToParent('pageChange', { page });
    }

    onPagesLoaded(event: PagesLoadedEvent): void {
        this.totalPages.set(event.pagesCount ?? 0);
        this.postMessageToParent('pagesLoaded', { pagesCount: event.pagesCount });
    }

    zoomIn(): void {
        this.dispatchZoomEvent('zoomin');
    }

    zoomOut(): void {
        this.dispatchZoomEvent('zoomout');
    }

    private dispatchZoomEvent(eventName: 'zoomin' | 'zoomout'): void {
        const pdfViewerApplication = this.pdfNotificationService.onPDFJSInitSignal();
        pdfViewerApplication?.eventBus?.dispatch(eventName);
    }

    private postMessageToParent(type: string, data: object): void {
        window.parent.postMessage({ type, ...data }, '*');
    }
}
