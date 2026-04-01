import { Location, NgTemplateOutlet } from '@angular/common';
import {
    ChangeDetectionStrategy,
    Component,
    DestroyRef,
    ElementRef,
    HostListener,
    Injector,
    afterNextRender,
    computed,
    effect,
    inject,
    input,
    output,
    signal,
    untracked,
    viewChild,
} from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import type { Dayjs } from 'dayjs/esm';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import type { IframeMessage, IframeMessageData, IframeMessageType } from './pdf-viewer-iframe.types';
import { PdfFullscreenOverlayService } from './pdf-fullscreen-overlay.service';

/**
 * Unified PDF viewer component that supports both embedded and fullscreen modes.
 * Replaces PdfViewerIframeWrapperComponent and PdfFullscreenOverlayComponent.
 */
@Component({
    selector: 'jhi-pdf-viewer',
    standalone: true,
    imports: [ArtemisDatePipe, ArtemisTranslatePipe, TranslateDirective, SafeResourceUrlPipe, NgTemplateOutlet, FaIconComponent],
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
    private readonly destroyRef = inject(DestroyRef);
    private readonly injector = inject(Injector);
    protected readonly fullscreenService = inject(PdfFullscreenOverlayService);

    // Internal state (embedded mode only)
    private readonly currentPage = signal(1);

    // Computed properties (mode-dependent)
    protected readonly effectiveUploadDate = computed(() => (this.mode() === 'embedded' ? this.uploadDate() : this.fullscreenService.fullscreenMetadata().uploadDate));

    protected readonly effectiveVersion = computed(() => (this.mode() === 'embedded' ? this.version() : this.fullscreenService.fullscreenMetadata().version));

    readonly iframeSrc = computed(() => this.location.prepareExternalUrl('pdf-viewer-iframe'));

    // Shared loading state for both modes
    protected readonly isLoading = signal(false);

    // For fullscreen mode
    readonly fullscreenMetadata = this.fullscreenService.fullscreenMetadata;
    protected readonly faXmark = faXmark;

    constructor() {
        let wasFullscreenOpen = false;
        let lastFullscreenPdfUrl: string | undefined;

        // Cleanup handler to prevent memory leaks when component is destroyed
        this.destroyRef.onDestroy(() => {
            this.close();
        });

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

        // Show loading state whenever a fullscreen PDF is opened.
        effect(() => {
            if (this.mode() !== 'fullscreen') {
                return;
            }

            const { isOpen, pdfUrl } = this.fullscreenService.fullscreenMetadata();
            if (isOpen && pdfUrl) {
                this.isLoading.set(true);
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

            const { isOpen, pdfUrl: fullscreenPdfUrl } = this.fullscreenService.fullscreenMetadata();
            if (isOpen && fullscreenPdfUrl) {
                lastFullscreenPdfUrl = fullscreenPdfUrl;
            }

            if (wasFullscreenOpen && !isOpen) {
                const page = this.fullscreenService.currentPage();
                const pdfUrl = this.pdfUrl();
                // Only restore if this viewer's PDF matches the one that was opened in fullscreen
                if (this.iframeReady() && pdfUrl && pdfUrl === lastFullscreenPdfUrl) {
                    this.currentPage.set(page);
                    this.loadPdf(pdfUrl, page);
                }
            }

            wasFullscreenOpen = isOpen;
        });

        // Auto-focus fullscreen window when rendered (for keyboard interactions)
        effect(() => {
            if (this.mode() !== 'fullscreen' || !this.fullscreenMetadata().isOpen) {
                return;
            }

            const fullscreenWindow = this.fullscreenWindow()?.nativeElement;
            if (fullscreenWindow) {
                afterNextRender(
                    () => {
                        fullscreenWindow.focus();
                    },
                    { injector: this.injector },
                );
            }
        });
    }

    // Close method (fullscreen mode only)
    close(): void {
        if (this.mode() === 'fullscreen') {
            this.fullscreenService.close();
            this.iframeReady.set(false);
            this.isLoading.set(false);
        }
    }

    @HostListener('window:message', ['$event'])
    protected onWindowMessage(event: MessageEvent<IframeMessage>): void {
        this.handleIframeMessage(event);
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
            // Don't hide spinner yet - wait for PDF to load
            return;
        }

        if (type === 'pageChange' && typeof data?.page === 'number' && Number.isInteger(data.page) && data.page > 0) {
            // Unified page tracking: Update current page based on mode
            this.setCurrentPage(data.page);
            return;
        }

        if (type === 'closeFullscreen') {
            this.close();
            return;
        }

        // Handle pagesLoaded - both modes need this to hide the loading spinner
        if (type === 'pagesLoaded') {
            this.isLoading.set(false);
            if (mode === 'embedded') {
                this.pagesLoaded.emit({
                    pdfUrl: data?.url ?? this.pdfUrl() ?? '',
                    pagesCount: data?.pagesCount ?? 0,
                });
            }
            return;
        }

        // Handle pdfLoadError
        if (type === 'pdfLoadError') {
            this.isLoading.set(false);
            if (mode === 'embedded') {
                this.loadError.emit({ pdfUrl: data?.url ?? this.pdfUrl() ?? '' });
            }
            return;
        }

        // Embedded-mode-only handlers
        if (mode !== 'embedded') {
            return;
        }

        switch (type) {
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

    /** Unified page tracking: Set current page based on mode */
    private setCurrentPage(page: number): void {
        if (this.mode() === 'embedded') {
            this.currentPage.set(page);
        } else {
            this.fullscreenService.updateCurrentPage(page);
        }
    }

    private loadPdf(url: string, page: number): void {
        const isDarkMode = untracked(() => this.themeService.currentTheme() === Theme.DARK);
        this.isLoading.set(true);

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
