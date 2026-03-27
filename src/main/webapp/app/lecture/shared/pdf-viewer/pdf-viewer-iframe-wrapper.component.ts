import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import type { Dayjs } from 'dayjs/esm';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
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
    imports: [ArtemisDatePipe, TranslateDirective, SafeResourceUrlPipe],
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

    readonly iframeSrc = computed(() => {
        return '/pdf-viewer-iframe';
    });

    private iframeLoadTimeoutId?: number;
    private readonly themeService = inject(ThemeService);

    constructor() {
        const destroyRef = inject(DestroyRef);

        // Effect to load PDF when iframe is ready and URL changes
        effect(() => {
            if (this.iframeReady() && this.pdfUrl()) {
                this.loadPdfInIframe();
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
            this.clearIframeLoadTimeout();
        });
    }

    /** Sets a safety timeout if iframe doesn't send "ready" within 10s. */
    onIframeLoad(): void {
        this.iframeLoadTimeoutId = window.setTimeout(() => {
            if (!this.iframeReady()) {
                this.iframeReady.set(true);
            }
        }, 10000);
    }

    /** Clears the iframe load timeout. */
    private clearIframeLoadTimeout(): void {
        if (this.iframeLoadTimeoutId !== undefined) {
            window.clearTimeout(this.iframeLoadTimeoutId);
            this.iframeLoadTimeoutId = undefined;
        }
    }

    /** Sends a loadPDF message to the iframe with current URL and theme. */
    private loadPdfInIframe(): void {
        const isDarkMode = this.themeService.currentTheme() === Theme.DARK;
        const url = this.pdfUrl();
        this.postMessageToIframe('loadPDF', {
            url: url,
            initialPage: this.initialPage() ?? 1,
            isDarkMode,
        });
    }

    /** Handles messages from the iframe, validating origin for security. */
    private readonly handleIframeMessage = (event: MessageEvent<IframeMessage>): void => {
        const iframe = this.pdfIframe()?.nativeElement;
        if (!iframe || event.origin !== window.location.origin || event.source !== iframe.contentWindow) {
            return;
        }

        const { type } = event.data;

        if (type === 'ready') {
            this.clearIframeLoadTimeout();
            this.iframeReady.set(true);
        } else if (type === 'pdfLoadError') {
            window.dispatchEvent(
                new CustomEvent('pdf-load-error', {
                    detail: { pdfUrl: this.pdfUrl() },
                }),
            );
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
