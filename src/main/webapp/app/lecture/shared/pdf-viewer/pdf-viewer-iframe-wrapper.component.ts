import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import type { Dayjs } from 'dayjs/esm';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';

type IframeMessageType = 'ready' | 'pageChange' | 'pagesLoaded' | 'loadPDF' | 'themeChange';

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

    onIframeLoad(): void {
        // Safety timeout: if iframe doesn't send "ready" within 10s, proceed anyway
        this.iframeLoadTimeoutId = window.setTimeout(() => {
            if (!this.iframeReady()) {
                globalThis.console.warn('PDF viewer iframe did not signal ready within 10 seconds, proceeding anyway');
                this.iframeReady.set(true);
            }
        }, 10000);
    }

    private clearIframeLoadTimeout(): void {
        if (this.iframeLoadTimeoutId !== undefined) {
            window.clearTimeout(this.iframeLoadTimeoutId);
            this.iframeLoadTimeoutId = undefined;
        }
    }

    private loadPdfInIframe(): void {
        // Send immediately - this only gets called when iframeReady is true,
        // which means we received the "ready" message and the listener is registered.
        const isDarkMode = this.themeService.currentTheme() === Theme.DARK;
        this.postMessageToIframe('loadPDF', {
            url: this.pdfUrl(),
            initialPage: this.initialPage() ?? 1,
            isDarkMode,
        });
    }

    private readonly handleIframeMessage = (event: MessageEvent<IframeMessage>): void => {
        const iframe = this.pdfIframe()?.nativeElement;
        // Validate both origin AND source for security
        if (!iframe || event.origin !== window.location.origin || event.source !== iframe.contentWindow) {
            return;
        }

        const { type } = event.data;

        if (type === 'ready') {
            // Iframe signals it's ready to receive messages.
            // Setting this triggers the effect which calls loadPdfInIframe().
            this.clearIframeLoadTimeout();
            this.iframeReady.set(true);
        }
    };

    private postMessageToIframe(type: IframeMessageType, data: IframeMessageData): void {
        const iframe = this.pdfIframe()?.nativeElement;
        if (iframe?.contentWindow) {
            iframe.contentWindow.postMessage({ type, data }, window.location.origin);
        }
    }
}
