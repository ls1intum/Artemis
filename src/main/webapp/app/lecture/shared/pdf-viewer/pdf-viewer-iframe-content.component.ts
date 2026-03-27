import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, ViewEncapsulation, inject, signal } from '@angular/core';
import { NgxExtendedPdfViewerModule, PDFNotificationService, type PagesLoadedEvent, pdfDefaultOptions } from 'ngx-extended-pdf-viewer';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faMagnifyingGlassMinus, faMagnifyingGlassPlus } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

pdfDefaultOptions.assetsFolder = 'assets/ngx-extended-pdf-viewer';

type IframeMessageType = 'ready' | 'pageChange' | 'pagesLoaded' | 'loadPDF' | 'themeChange' | 'pdfLoadError';

interface IframeMessageData {
    page?: number;
    pagesCount?: number;
    url?: string;
    initialPage?: number;
    isDarkMode?: boolean;
}

interface IframeMessage {
    type: IframeMessageType;
    data?: IframeMessageData;
}

/**
 * Standalone PDF viewer component that runs inside an iframe.
 * This allows multiple instances of ngx-extended-pdf-viewer on the same page.
 */
@Component({
    selector: 'jhi-pdf-viewer-iframe-content',
    standalone: true,
    imports: [NgxExtendedPdfViewerModule, FaIconComponent, ArtemisTranslatePipe],
    templateUrl: './pdf-viewer-iframe-content.component.html',
    styleUrls: ['./pdf-viewer-iframe-content.component.scss'],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfViewerIframeContentComponent implements OnInit {
    private readonly pdfNotificationService = inject(PDFNotificationService);
    private readonly destroyRef = inject(DestroyRef);

    readonly pdfUrl = signal<string>('');
    readonly initialPage = signal<number>(1);
    readonly currentPage = signal<number>(1);
    readonly totalPages = signal<number>(0);
    readonly isDarkMode = signal<boolean>(false);

    protected readonly faMagnifyingGlassMinus = faMagnifyingGlassMinus;
    protected readonly faMagnifyingGlassPlus = faMagnifyingGlassPlus;

    /** Sets up message listeners and notifies parent that iframe is ready. */
    ngOnInit(): void {
        const messageHandler = (event: MessageEvent) => {
            this.handleParentMessage(event);
        };

        window.addEventListener('message', messageHandler);
        this.destroyRef.onDestroy(() => {
            window.removeEventListener('message', messageHandler);
        });

        this.postMessageToParent('ready', {});
    }

    /** Handles messages from parent window, validating origin for security. */
    private readonly handleParentMessage = (event: MessageEvent<IframeMessage>): void => {
        if (event.origin !== window.location.origin) {
            return;
        }

        if (!event.data || typeof event.data !== 'object') {
            return;
        }

        const { type, data } = event.data;

        switch (type) {
            case 'loadPDF':
                if (data?.url && data.url !== this.pdfUrl()) {
                    this.pdfUrl.set(data.url);
                    const pageToLoad = data.initialPage ?? 1;
                    this.initialPage.set(pageToLoad);
                    this.currentPage.set(pageToLoad);
                }
                if (data?.isDarkMode !== undefined && data.isDarkMode !== this.isDarkMode()) {
                    this.isDarkMode.set(data.isDarkMode);
                }
                break;
            case 'themeChange':
                if (data?.isDarkMode !== undefined && data.isDarkMode !== this.isDarkMode()) {
                    this.isDarkMode.set(data.isDarkMode);
                }
                break;
        }
    };

    /** Updates current page and notifies parent. */
    onPageChange(page: number): void {
        this.currentPage.set(page);
        this.postMessageToParent('pageChange', { page });
    }

    /** Updates total page count and notifies parent. */
    onPagesLoaded(event: PagesLoadedEvent): void {
        this.totalPages.set(event.pagesCount ?? 0);
        this.postMessageToParent('pagesLoaded', { pagesCount: event.pagesCount ?? 0 });
    }

    /** Notifies parent of load failure to trigger blob fallback. */
    onPdfLoadingFailed(): void {
        this.postMessageToParent('pdfLoadError', {});
    }

    /** Zooms in on the PDF. */
    zoomIn(): void {
        this.dispatchZoomEvent('zoomin');
    }

    /** Zooms out on the PDF. */
    zoomOut(): void {
        this.dispatchZoomEvent('zoomout');
    }

    /** Dispatches a zoom event to the PDF.js event bus. */
    private dispatchZoomEvent(eventName: 'zoomin' | 'zoomout'): void {
        const pdfViewerApplication = this.pdfNotificationService.onPDFJSInitSignal() as any;
        const eventBus = pdfViewerApplication?.eventBus;
        if (!eventBus) {
            return;
        }

        const container = pdfViewerApplication?.appConfig?.mainContainer ?? pdfViewerApplication?.pdfViewer?.container ?? document.getElementById('viewerContainer');
        const currentScale = pdfViewerApplication?.pdfViewer?.currentScale;

        if (!container || !currentScale || !container.clientWidth || !container.clientHeight) {
            eventBus.dispatch(eventName);
            return;
        }

        const centerX = container.scrollLeft + container.clientWidth / 2;
        const centerY = container.scrollTop + container.clientHeight / 2;

        eventBus.dispatch(eventName);
        requestAnimationFrame(() => {
            const nextScale = pdfViewerApplication?.pdfViewer?.currentScale ?? currentScale;
            const scaleFactor = nextScale / currentScale;
            container.scrollLeft = Math.max(0, centerX * scaleFactor - container.clientWidth / 2);
            container.scrollTop = Math.max(0, centerY * scaleFactor - container.clientHeight / 2);
        });
    }

    /** Posts a message to the parent window. */
    private postMessageToParent(type: IframeMessageType, data: IframeMessageData): void {
        window.parent.postMessage({ type, data }, window.location.origin);
    }
}
