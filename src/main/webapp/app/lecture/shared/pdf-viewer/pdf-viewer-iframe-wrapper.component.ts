import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import type { Dayjs } from 'dayjs/esm';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';

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
 * Wrapper component that loads the PDF viewer in an iframe.
 * This allows multiple PDF viewer instances on the same page,
 * circumventing ngx-extended-pdf-viewer's single-instance limitation.
 */
@Component({
    selector: 'jhi-lecture-pdf-viewer-iframe',
    standalone: true,
    imports: [ArtemisDatePipe, ArtemisTranslatePipe, TranslateDirective, SafeResourceUrlPipe],
    templateUrl: './pdf-viewer-iframe-wrapper.component.html',
    styleUrls: ['./pdf-viewer-iframe-wrapper.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfViewerIframeWrapperComponent {
    pdfUrl = input.required<string>();
    uploadDate = input<Dayjs | undefined>(undefined);
    version = input<number | undefined>(undefined);
    initialPage = input<number | undefined>(undefined);

    readonly pdfIframe = viewChild<ElementRef<HTMLIFrameElement>>('pdfIframe');
    readonly iframeReady = signal(false);
    readonly ready = output<{ pdfUrl: string }>();
    readonly loadError = output<{ pdfUrl: string }>();

    readonly iframeSrc = computed(() => {
        return '/pdf-viewer-iframe';
    });

    private readonly themeService = inject(ThemeService);

    constructor() {
        const destroyRef = inject(DestroyRef);

        // Effect to load PDF when iframe is ready and URL changes
        effect(() => {
            const initialPage = this.initialPage();
            if (this.iframeReady() && this.pdfUrl()) {
                this.loadPdfInIframe(initialPage);
            }
        });

        // Effect to notify iframe of theme changes
        effect(() => {
            const isDarkMode = this.themeService.currentTheme() === Theme.DARK;
            if (this.iframeReady()) {
                this.postMessageToIframe('themeChange', { isDarkMode });
            }
        });

        const messageHandler = (event: MessageEvent<IframeMessage>) => {
            this.handleIframeMessage(event);
        };

        window.addEventListener('message', messageHandler);
        destroyRef.onDestroy(() => {
            window.removeEventListener('message', messageHandler);
        });
    }

    /** Sends a loadPDF message to the iframe with current URL and theme. */
    private loadPdfInIframe(initialPage?: number): void {
        const isDarkMode = untracked(() => this.themeService.currentTheme() === Theme.DARK);
        const url = this.pdfUrl();
        this.postMessageToIframe('loadPDF', {
            url: url,
            initialPage: initialPage ?? 1,
            isDarkMode,
        });
    }

    /** Handles messages from the iframe, validating origin for security. */
    private readonly handleIframeMessage = (event: MessageEvent<IframeMessage>): void => {
        const iframe = this.pdfIframe()?.nativeElement;
        if (!iframe || event.origin !== window.location.origin || event.source !== iframe.contentWindow) {
            return;
        }

        if (!event.data || typeof event.data !== 'object') {
            return;
        }

        const { type } = event.data;

        if (type === 'ready') {
            this.iframeReady.set(true);
            this.ready.emit({ pdfUrl: this.pdfUrl() });
        } else if (type === 'pdfLoadError') {
            this.loadError.emit({ pdfUrl: this.pdfUrl() });
        }
    };

    /** Posts a message to the iframe content window. */
    private postMessageToIframe(type: IframeMessageType, data: IframeMessageData): void {
        const iframe = this.pdfIframe()?.nativeElement;
        if (iframe?.contentWindow) {
            iframe.contentWindow.postMessage({ type, data }, window.location.origin);
        }
    }
}
