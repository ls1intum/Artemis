import { Location, NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
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
    readonly fullscreenWindow = viewChild<ElementRef<HTMLDivElement>>('fullscreenWindow');
    readonly iframeReady = signal(false);

    // Services
    private readonly themeService = inject(ThemeService);
    private readonly location = inject(Location);
    private readonly fullscreenService = inject(PdfFullscreenOverlayService);

    // Internal state (embedded mode only)
    private readonly currentPage = signal(1);

    // Computed properties (mode-dependent)
    protected readonly effectiveUploadDate = computed(() => (this.mode() === 'embedded' ? this.uploadDate() : this.fullscreenService.fullscreenMetadata().uploadDate));

    protected readonly effectiveVersion = computed(() => (this.mode() === 'embedded' ? this.version() : this.fullscreenService.fullscreenMetadata().version));

    readonly iframeSrc = computed(() => this.location.prepareExternalUrl('pdf-viewer-iframe'));

    // For fullscreen mode
    readonly fullscreenMetadata = this.fullscreenService.fullscreenMetadata;

    // Icons (for fullscreen mode)
    protected readonly faXmark = faXmark;

    constructor() {
        let wasFullscreenOpen = false;

        // Load PDF when iframe ready
        effect(() => {
            if (!this.iframeReady()) {
                return;
            }

            if (this.mode() === 'fullscreen') {
                const { isOpen, pdfUrl } = this.fullscreenService.fullscreenMetadata();
                if (!isOpen || !pdfUrl) {
                    return;
                }

                // Use untracked to avoid reloading when page changes
                const page = untracked(() => this.fullscreenService.currentPage());
                this.loadPdf(pdfUrl, page);
                return;
            }

            const pdfUrl = this.pdfUrl();
            if (pdfUrl) {
                this.loadPdf(pdfUrl, this.initialPage() ?? 1);
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

            if (wasFullscreenOpen && !isOpen) {
                const page = this.fullscreenService.currentPage();
                const pdfUrl = this.pdfUrl();
                if (this.iframeReady() && pdfUrl) {
                    this.loadPdf(pdfUrl, page);
                }
            }

            wasFullscreenOpen = isOpen;
        });

        // Auto-focus fullscreen window when opened (for ESC key to work)
        effect(() => {
            if (this.mode() === 'fullscreen' && this.fullscreenMetadata().isOpen) {
                const windowElement = this.fullscreenWindow()?.nativeElement;
                if (windowElement) {
                    // Use setTimeout to ensure the element is fully rendered
                    setTimeout(() => windowElement.focus(), 0);
                }
            }
        });
    }

    // Close method (fullscreen mode only)
    close(): void {
        if (this.mode() === 'fullscreen') {
            this.fullscreenService.close();
            this.iframeReady.set(false);
        }
    }

    @HostListener('window:message', ['$event'])
    protected onWindowMessage(event: MessageEvent<IframeMessage>): void {
        this.handleIframeMessage(event);
    }

    @HostListener('window:keydown', ['$event'])
    protected onEscapeKey(event: KeyboardEvent): void {
        // Only close if fullscreen is actually visible (not just mode='fullscreen')
        if (event.key === 'Escape' && this.mode() === 'fullscreen' && this.fullscreenWindow()?.nativeElement) {
            event.preventDefault();
            this.close();
        }
    }

    /** Handles iframe messages and ignores messages from invalid origins/sources. */
    private handleIframeMessage(event: MessageEvent<IframeMessage>): void {
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
        const mode = this.mode();

        // Common handlers
        if (type === 'ready') {
            this.iframeReady.set(true);
            return;
        }

        if (type === 'pageChange' && typeof data?.page === 'number' && Number.isInteger(data.page) && data.page > 0) {
            if (mode === 'embedded') {
                this.currentPage.set(data.page);
            } else {
                this.fullscreenService.updateCurrentPage(data.page);
            }
            return;
        }

        if (type === 'closeFullscreen') {
            this.close();
            return;
        }

        if (mode !== 'embedded') {
            return;
        }

        switch (type) {
            case 'pagesLoaded':
                this.pagesLoaded.emit({
                    pdfUrl: data?.url ?? this.pdfUrl() ?? '',
                    pagesCount: data?.pagesCount ?? 0,
                });
                break;
            case 'pdfLoadError':
                this.loadError.emit({ pdfUrl: data?.url ?? this.pdfUrl() ?? '' });
                break;
            case 'download':
                this.downloadRequested.emit();
                break;
            case 'openFullscreen':
                this.openFullscreen();
                break;
        }
    }

    private openFullscreen(): void {
        if (this.mode() !== 'embedded') return;

        const pdfUrl = this.pdfUrl();
        if (!pdfUrl) return;

        const currentPage = this.currentPage() || this.initialPage() || 1;
        this.fullscreenService.open(pdfUrl, currentPage, this.uploadDate(), this.version());
    }

    private loadPdf(url: string, page: number): void {
        const isDarkMode = untracked(() => this.themeService.currentTheme() === Theme.DARK);

        this.postMessageToIframe('loadPDF', {
            url,
            initialPage: page,
            isDarkMode,
            viewerMode: this.mode(),
        });
    }

    private postMessageToIframe(type: IframeMessageType, data: IframeMessageData): void {
        const iframe = this.pdfIframe()?.nativeElement;
        if (iframe?.contentWindow) {
            iframe.contentWindow.postMessage({ type, data }, window.location.origin);
        }
    }
}
