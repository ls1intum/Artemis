import { Location, NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, HostBinding, computed, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import type { Dayjs } from 'dayjs/esm';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import type { IframeMessage, IframeMessageData, IframeMessageType } from './pdf-viewer-iframe.types';
import { PdfFullscreenOverlayService } from './pdf-fullscreen-overlay.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';

/**
 * Unified PDF viewer component that supports both embedded and fullscreen modes.
 * Replaces PdfViewerIframeWrapperComponent and PdfFullscreenOverlayComponent.
 */
@Component({
    selector: 'jhi-pdf-viewer',
    standalone: true,
    imports: [ArtemisDatePipe, ArtemisTranslatePipe, TranslateDirective, SafeResourceUrlPipe, FaIconComponent, NgTemplateOutlet],
    templateUrl: './pdf-viewer.component.html',
    styleUrls: ['./pdf-viewer.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfViewerComponent {
    // Mode: embedded (inline) or fullscreen (overlay)
    mode = input<'embedded' | 'fullscreen'>('embedded');

    // For embedded mode (inputs)
    pdfUrl = input<string | undefined>(undefined);
    uploadDate = input<Dayjs | undefined>(undefined);
    version = input<number | undefined>(undefined);
    initialPage = input<number | undefined>(undefined);

    // For embedded mode (outputs)
    readonly pagesLoaded = output<{ pdfUrl: string; pagesCount: number }>();
    readonly loadError = output<{ pdfUrl: string }>();
    readonly downloadRequested = output<void>();

    // Shared state
    readonly pdfIframe = viewChild<ElementRef<HTMLIFrameElement>>('pdfIframe');
    readonly iframeReady = signal(false);

    // Services
    private readonly themeService = inject(ThemeService);
    private readonly location = inject(Location);
    private readonly fullscreenService = inject(PdfFullscreenOverlayService);

    // Internal state (embedded mode only)
    private readonly currentPage = signal(1);
    private readonly wasFullscreenOpen = signal(false);

    // Computed properties (mode-dependent)
    private readonly effectivePdfUrl = computed(() => (this.mode() === 'embedded' ? this.pdfUrl() : this.fullscreenService.fullscreenMetadata().pdfUrl));

    protected readonly effectiveUploadDate = computed(() => (this.mode() === 'embedded' ? this.uploadDate() : this.fullscreenService.fullscreenMetadata().uploadDate));

    protected readonly effectiveVersion = computed(() => (this.mode() === 'embedded' ? this.version() : this.fullscreenService.fullscreenMetadata().version));

    private readonly effectiveInitialPage = computed(() => (this.mode() === 'embedded' ? this.initialPage() : this.fullscreenService.currentPage()));

    readonly iframeSrc = computed(() => this.location.prepareExternalUrl('pdf-viewer-iframe'));

    // For fullscreen mode
    readonly fullscreenMetadata = this.fullscreenService.fullscreenMetadata;

    // Icons (for fullscreen mode)
    protected readonly faXmark = faXmark;

    @HostBinding('class.fullscreen-mode')
    get fullscreenModeClass() {
        return this.mode() === 'fullscreen' && this.fullscreenService.fullscreenMetadata().isOpen;
    }

    constructor() {
        const destroyRef = inject(DestroyRef);

        // Load PDF when iframe ready
        effect(() => {
            // For fullscreen mode, also check if isOpen
            if (this.mode() === 'fullscreen') {
                const { isOpen, pdfUrl } = this.fullscreenService.fullscreenMetadata();
                if (isOpen && this.iframeReady() && pdfUrl) {
                    // Use untracked to avoid reloading when page changes
                    const page = untracked(() => this.fullscreenService.currentPage());
                    this.loadPdf(pdfUrl, page);
                }
            } else {
                // Embedded mode
                const pdfUrl = this.effectivePdfUrl();
                const initialPage = this.effectiveInitialPage();
                if (this.iframeReady() && pdfUrl) {
                    this.loadPdf(pdfUrl, initialPage ?? 1);
                }
            }
        });

        // Sync theme changes
        effect(() => {
            const isDarkMode = this.themeService.currentTheme() === Theme.DARK;
            if (this.iframeReady()) {
                this.postMessageToIframe('themeChange', { isDarkMode });
            }
        });

        // Restore page when fullscreen closes (embedded mode only)
        effect(() => {
            if (this.mode() !== 'embedded') return;

            const isOpen = this.fullscreenService.fullscreenMetadata().isOpen;
            const wasOpen = this.wasFullscreenOpen();

            if (wasOpen && !isOpen) {
                const page = this.fullscreenService.currentPage();
                if (this.iframeReady() && this.effectivePdfUrl()) {
                    this.loadPdf(this.effectivePdfUrl()!, page);
                }
            }

            this.wasFullscreenOpen.set(isOpen);
        });

        const messageListener = (event: Event) => {
            this.handleIframeMessage(event as MessageEvent<IframeMessage>);
        };

        window.addEventListener('message', messageListener);
        destroyRef.onDestroy(() => {
            window.removeEventListener('message', messageListener);
        });
    }

    // Close method (fullscreen mode only)
    close(): void {
        if (this.mode() === 'fullscreen' && this.fullscreenService.fullscreenMetadata().isOpen) {
            this.fullscreenService.close();
            this.iframeReady.set(false);
        }
    }

    /** Handles iframe messages and ignores messages from invalid origins/sources. */
    private readonly handleIframeMessage = (event: MessageEvent<IframeMessage>): void => {
        // Origin validation
        if (event.origin !== window.location.origin) {
            return;
        }

        // Source validation
        const iframe = this.pdfIframe()?.nativeElement;
        if (!iframe?.contentWindow || event.source !== iframe.contentWindow) {
            return;
        }

        if (!event.data || typeof event.data !== 'object') {
            return;
        }

        const { type, data } = event.data;

        // Common handlers
        if (type === 'ready') {
            this.iframeReady.set(true);
            return;
        }

        if (type === 'pageChange' && typeof data?.page === 'number' && Number.isInteger(data.page) && data.page > 0) {
            if (this.mode() === 'embedded') {
                this.currentPage.set(data.page);
            } else {
                this.fullscreenService.updateCurrentPage(data.page);
            }
            return;
        }

        // Embedded-mode-only handlers
        if (this.mode() === 'embedded') {
            switch (type) {
                case 'pagesLoaded':
                    this.pagesLoaded.emit({
                        pdfUrl: data?.url ?? this.effectivePdfUrl()!,
                        pagesCount: data?.pagesCount ?? 0,
                    });
                    break;
                case 'pdfLoadError':
                    this.loadError.emit({ pdfUrl: data?.url ?? this.effectivePdfUrl()! });
                    break;
                case 'download':
                    this.downloadRequested.emit();
                    break;
                case 'openFullscreen':
                    this.openFullscreen();
                    break;
            }
        }
    };

    private openFullscreen(): void {
        if (this.mode() !== 'embedded') return;

        const pdfUrl = this.effectivePdfUrl();
        if (!pdfUrl) return;

        const currentPage = this.currentPage() || this.initialPage() || 1;
        this.fullscreenService.open(pdfUrl, currentPage, this.uploadDate(), this.version());
    }

    private loadPdf(url: string, page: number): void {
        const isDarkMode = untracked(() => this.themeService.currentTheme() === Theme.DARK);
        const viewerMode = this.mode() === 'embedded' ? 'embedded' : 'fullscreen';

        this.postMessageToIframe('loadPDF', {
            url,
            initialPage: page,
            isDarkMode,
            viewerMode,
        });
    }

    private postMessageToIframe(type: IframeMessageType, data: IframeMessageData): void {
        const iframe = this.pdfIframe()?.nativeElement;
        if (iframe?.contentWindow) {
            iframe.contentWindow.postMessage({ type, data }, window.location.origin);
        }
    }
}
