import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import type { Dayjs } from 'dayjs/esm';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import type { IframeMessage, IframeMessageData, IframeMessageType } from './pdf-viewer-iframe.types';

/** Wrapper for the iframe PDF viewer so multiple viewer instances can run simultaneously. */
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
    readonly pagesLoaded = output<{ pdfUrl: string; pagesCount: number }>();
    readonly loadError = output<{ pdfUrl: string }>();
    readonly downloadRequested = output<void>();

    private readonly themeService = inject(ThemeService);
    private readonly location = inject(Location);

    readonly iframeSrc = computed(() => this.location.prepareExternalUrl('pdf-viewer-iframe'));

    constructor() {
        const destroyRef = inject(DestroyRef);

        effect(() => {
            const initialPage = this.initialPage();
            if (this.iframeReady() && this.pdfUrl()) {
                this.loadPdfInIframe(initialPage);
            }
        });

        effect(() => {
            const isDarkMode = this.themeService.currentTheme() === Theme.DARK;
            if (this.iframeReady()) {
                this.postMessageToIframe('themeChange', { isDarkMode });
            }
        });

        window.addEventListener('message', this.handleIframeMessage as EventListener);
        destroyRef.onDestroy(() => {
            window.removeEventListener('message', this.handleIframeMessage as EventListener);
        });
    }

    private loadPdfInIframe(initialPage?: number): void {
        const isDarkMode = untracked(() => this.themeService.currentTheme() === Theme.DARK);
        this.postMessageToIframe('loadPDF', {
            url: this.pdfUrl(),
            initialPage: initialPage ?? 1,
            isDarkMode,
        });
    }

    /** Handles iframe messages and ignores messages from invalid origins/sources. */
    private readonly handleIframeMessage = (event: MessageEvent<IframeMessage>): void => {
        const iframe = this.pdfIframe()?.nativeElement;
        if (!iframe || event.origin !== window.location.origin || event.source !== iframe.contentWindow) {
            return;
        }

        if (!event.data || typeof event.data !== 'object') {
            return;
        }

        const { type, data } = event.data;

        switch (type) {
            case 'ready':
                this.iframeReady.set(true);
                break;
            case 'pagesLoaded':
                this.pagesLoaded.emit({ pdfUrl: data?.url ?? this.pdfUrl(), pagesCount: data?.pagesCount ?? 0 });
                break;
            case 'pdfLoadError':
                this.loadError.emit({ pdfUrl: data?.url ?? this.pdfUrl() });
                break;
            case 'download':
                this.downloadRequested.emit();
                break;
        }
    };

    private postMessageToIframe(type: IframeMessageType, data: IframeMessageData): void {
        const iframe = this.pdfIframe()?.nativeElement;
        if (iframe?.contentWindow) {
            iframe.contentWindow.postMessage({ type, data }, window.location.origin);
        }
    }
}
