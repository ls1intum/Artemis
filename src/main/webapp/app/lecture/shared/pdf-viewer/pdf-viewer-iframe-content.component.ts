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

    /**
     * Initializes the component by setting up message listeners and notifying the parent that the iframe is ready.
     */
    ngOnInit(): void {
        const messageHandler = (event: MessageEvent) => {
            this.handleParentMessage(event);
        };

        window.addEventListener('message', messageHandler);
        this.destroyRef.onDestroy(() => {
            window.removeEventListener('message', messageHandler);
        });

        // Notify parent that iframe is ready
        this.postMessageToParent('ready', {});
    }

    /**
     * Handles messages received from the parent window.
     * Validates the message origin for security, then processes loadPDF and themeChange events.
     *
     * @param event - The message event from the parent window
     */
    private readonly handleParentMessage = (event: MessageEvent<IframeMessage>): void => {
        // Validate origin FIRST (security-critical)
        if (event.origin !== window.location.origin) {
            return;
        }

        if (!event.data || typeof event.data !== 'object') {
            return;
        }

        const { type, data } = event.data;

        switch (type) {
            case 'loadPDF':
                if (data?.url) {
                    this.pdfUrl.set(data.url);
                    const pageToLoad = data.initialPage ?? 1;
                    this.initialPage.set(pageToLoad);
                    this.currentPage.set(pageToLoad);
                }
                if (data?.isDarkMode !== undefined) {
                    this.isDarkMode.set(data.isDarkMode);
                }
                break;
            case 'themeChange':
                if (data?.isDarkMode !== undefined) {
                    this.isDarkMode.set(data.isDarkMode);
                }
                break;
        }
    };

    /**
     * Called when the user navigates to a different page in the PDF.
     * Updates the current page and notifies the parent window.
     *
     * @param page - The new page number
     */
    onPageChange(page: number): void {
        this.currentPage.set(page);
        this.postMessageToParent('pageChange', { page });
    }

    /**
     * Called when the PDF finishes loading.
     * Updates the total page count and notifies the parent window.
     *
     * @param event - The pages loaded event containing the total page count
     */
    onPagesLoaded(event: PagesLoadedEvent): void {
        this.totalPages.set(event.pagesCount ?? 0);
        this.postMessageToParent('pagesLoaded', { pagesCount: event.pagesCount ?? 0 });
    }

    /**
     * Called when the PDF fails to load.
     * Notifies the parent window to trigger the blob fallback mechanism.
     */
    onPdfLoadingFailed(): void {
        // Notify parent about load failure to trigger blob fallback
        this.postMessageToParent('pdfLoadError', {});
    }

    /**
     * Zooms in on the PDF by dispatching a zoom-in event to the PDF viewer.
     */
    zoomIn(): void {
        this.dispatchZoomEvent('zoomin');
    }

    /**
     * Zooms out on the PDF by dispatching a zoom-out event to the PDF viewer.
     */
    zoomOut(): void {
        this.dispatchZoomEvent('zoomout');
    }

    /**
     * Dispatches a zoom event to the PDF.js event bus.
     *
     * @param eventName - The zoom event name ('zoomin' or 'zoomout')
     */
    private dispatchZoomEvent(eventName: 'zoomin' | 'zoomout'): void {
        const pdfViewerApplication = this.pdfNotificationService.onPDFJSInitSignal();
        if (!pdfViewerApplication?.eventBus) {
            return;
        }
        pdfViewerApplication.eventBus.dispatch(eventName);
    }

    /**
     * Sends a message to the parent window.
     *
     * @param type - The type of message to send
     * @param data - The message data payload
     */
    private postMessageToParent(type: IframeMessageType, data: IframeMessageData): void {
        window.parent.postMessage({ type, data }, window.location.origin);
    }
}
