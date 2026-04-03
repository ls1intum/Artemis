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
    imports: [ArtemisDatePipe, ArtemisTranslatePipe, TranslateDirective, SafeResourceUrlPipe, NgTemplateOutlet],
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

    constructor() {
        this.registerDestroyCleanup();
        this.setupPdfLoadingEffect();
        this.setupFullscreenLoadingEffect();
        this.setupThemeSyncEffect();
        this.setupPageRestoreEffect();
        this.setupFullscreenFocusEffect();
    }

    private registerDestroyCleanup(): void {
        this.destroyRef.onDestroy(() => {
            this.close();
        });
    }

    private setupPdfLoadingEffect(): void {
        effect(() => {
            if (!this.iframeReady()) {
                return;
            }

            if (this.mode() === 'fullscreen') {
                const { isOpen, pdfUrl } = this.fullscreenService.fullscreenMetadata();
                if (!isOpen || !pdfUrl) {
                    return;
                }

                const page = untracked(() => this.fullscreenService.currentPage());
                this.loadPdf(pdfUrl, page);
                return;
            }

            const pdfUrl = this.pdfUrl();
            if (pdfUrl) {
                this.loadPdf(pdfUrl, this.initialPage() ?? 1);
            }
        });
    }

    private setupFullscreenLoadingEffect(): void {
        effect(() => {
            if (this.mode() !== 'fullscreen') {
                return;
            }

            const { isOpen, pdfUrl } = this.fullscreenService.fullscreenMetadata();
            if (isOpen && pdfUrl) {
                this.isLoading.set(true);
            }
        });
    }

    private setupThemeSyncEffect(): void {
        effect(() => {
            const isDarkMode = this.themeService.currentTheme() === Theme.DARK;
            if (this.iframeReady()) {
                this.postMessageToIframe('themeChange', { isDarkMode });
            }
        });
    }

    private setupPageRestoreEffect(): void {
        let wasFullscreenOpen = false;
        let lastFullscreenPdfUrl: string | undefined;

        effect(() => {
            if (this.mode() !== 'embedded') {
                return;
            }

            const { isOpen, pdfUrl: fullscreenPdfUrl } = this.fullscreenService.fullscreenMetadata();
            if (isOpen && fullscreenPdfUrl) {
                lastFullscreenPdfUrl = fullscreenPdfUrl;
            }

            if (wasFullscreenOpen && !isOpen) {
                const page = this.fullscreenService.currentPage();
                const pdfUrl = this.pdfUrl();
                if (this.iframeReady() && pdfUrl && pdfUrl === lastFullscreenPdfUrl) {
                    this.currentPage.set(page);
                    this.loadPdf(pdfUrl, page);
                }
            }

            wasFullscreenOpen = isOpen;
        });
    }

    private setupFullscreenFocusEffect(): void {
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
            const wasReady = this.iframeReady();
            this.iframeReady.set(true);
            // Drag & drop in instructor view can re-initialize the iframe; when this happens, the current PDF must be loaded again.
            if (wasReady) {
                this.reloadCurrentPdf();
            }
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

        if (type === 'download') {
            if (mode === 'embedded') {
                this.downloadRequested.emit();
            } else {
                this.fullscreenService.triggerDownloadRequested();
            }
            return;
        }

        // Embedded-mode-only handlers
        if (mode !== 'embedded') {
            return;
        }

        switch (type) {
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
        this.fullscreenService.open(pdfUrl, currentPage, this.uploadDate(), this.version(), () => {
            this.downloadRequested.emit();
        });
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
        this.setCurrentPage(page);

        this.postMessageToIframe('loadPDF', {
            url,
            initialPage: page,
            isDarkMode,
            viewerMode: this.mode(),
        });
    }

    private reloadCurrentPdf(): void {
        if (this.mode() === 'fullscreen') {
            const { isOpen, pdfUrl } = this.fullscreenService.fullscreenMetadata();
            if (!isOpen || !pdfUrl) {
                return;
            }

            const page = this.fullscreenService.currentPage();
            this.loadPdf(pdfUrl, page);
            return;
        }

        const pdfUrl = this.pdfUrl();
        if (!pdfUrl) {
            return;
        }

        const page = this.currentPage() || this.initialPage() || 1;
        this.loadPdf(pdfUrl, page);
    }

    private postMessageToIframe(type: IframeMessageType, data: IframeMessageData): void {
        const iframe = this.pdfIframe()?.nativeElement;
        if (iframe?.contentWindow) {
            iframe.contentWindow.postMessage({ type, data }, window.location.origin);
        }
    }
}
