import { Component, ElementRef, OnDestroy, OnInit, computed, effect, input, signal, viewChild } from '@angular/core';
import type { Dayjs } from 'dayjs/esm';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';

interface IframeMessage {
    type: string;
    [key: string]: unknown;
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
})
export class PdfViewerIframeWrapperComponent implements OnInit, OnDestroy {
    pdfUrl = input.required<string>();
    uploadDate = input<Dayjs | undefined>(undefined);
    version = input<number | undefined>(undefined);
    initialPage = input<number | undefined>(undefined);

    readonly pdfIframe = viewChild<ElementRef<HTMLIFrameElement>>('pdfIframe');
    readonly iframeReady = signal(false);

    readonly iframeSrc = computed(() => {
        // Use path-based URL for Angular routing within iframe
        return '/pdf-viewer-iframe';
    });

    constructor() {
        // Effect to load PDF when iframe is ready and URL changes
        effect(() => {
            if (this.iframeReady() && this.pdfUrl()) {
                this.loadPdfInIframe();
            }
        });
    }

    ngOnInit(): void {
        window.addEventListener('message', this.handleIframeMessage);
    }

    ngOnDestroy(): void {
        window.removeEventListener('message', this.handleIframeMessage);
    }

    onIframeLoad(): void {
        // Wait for explicit "ready" message from iframe content component.
        // No timeout needed - the iframe will signal when it's ready.
    }

    private loadPdfInIframe(): void {
        // Send immediately - this only gets called when iframeReady is true,
        // which means we received the "ready" message and the listener is registered.
        this.postMessageToIframe('loadPDF', {
            url: this.pdfUrl(),
            initialPage: this.initialPage() || 1,
        });
    }

    private readonly handleIframeMessage = (event: MessageEvent<IframeMessage>): void => {
        // Verify the message is from our iframe
        const iframe = this.pdfIframe()?.nativeElement;
        if (!iframe || event.source !== iframe.contentWindow) {
            return;
        }

        const { type } = event.data;

        switch (type) {
            case 'ready':
                // Iframe signals it's ready to receive messages.
                // Setting this triggers the effect which calls loadPdfInIframe().
                this.iframeReady.set(true);
                break;
        }
    };

    private postMessageToIframe(type: string, data: object): void {
        const iframe = this.pdfIframe()?.nativeElement;
        if (iframe?.contentWindow) {
            iframe.contentWindow.postMessage({ type, data }, '*');
        }
    }
}
