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
import { toSignal } from '@angular/core/rxjs-interop';
import type { Dayjs } from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import type { IframeMessage, IframeMessageData, IframeMessageType } from './pdf-viewer-iframe.types';

/**
 * Single-instance PDF viewer that can toggle between embedded and fullscreen display
 * without creating a second iframe or reloading the document.
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
    // Inputs
    pdfUrl = input<string | undefined>(undefined);
    uploadDate = input<Dayjs | undefined>(undefined);
    version = input<number | undefined>(undefined);
    initialPage = input<number | undefined>(undefined);

    // Outputs
    readonly pageRendered = output<{ pdfUrl: string }>();
    readonly loadError = output<{ pdfUrl: string }>();
    readonly downloadRequested = output<void>();
    readonly isFullscreenChange = output<boolean>();

    readonly pdfIframe = viewChild<ElementRef<HTMLIFrameElement>>('pdfIframe');
    readonly fullscreenWindow = viewChild<ElementRef<HTMLDivElement>>('fullscreenWindow');
    readonly iframeReady = signal(false);
    readonly isFullscreen = signal(false);

    protected readonly isLoading = signal(false);

    private readonly themeService = inject(ThemeService);
    private readonly translateService = inject(TranslateService);
    private readonly location = inject(Location);
    private readonly destroyRef = inject(DestroyRef);
    private readonly injector = inject(Injector);
    private readonly hostElementRef = inject(ElementRef<HTMLElement>);
    private readonly languageChange = toSignal(this.translateService.onLangChange);
    private readonly currentPage = signal(1);
    private drawerContentElement?: HTMLElement;
    private originalDrawerContentZIndex?: string;

    protected readonly effectiveUploadDate = computed(() => this.uploadDate());
    protected readonly effectiveVersion = computed(() => this.version());
    readonly iframeSrc = computed(() => this.location.prepareExternalUrl('pdf-viewer-iframe'));

    constructor() {
        this.registerDestroyCleanup();
        this.setupPdfLoadingEffect();
        this.setupThemeSyncEffect();
        this.setupLanguageSyncEffect();
        this.setupViewerModeSyncEffect();
        this.setupFullscreenFocusEffect();
    }

    private registerDestroyCleanup(): void {
        this.destroyRef.onDestroy(() => {
            this.closeFullscreen();
        });
    }

    private setupPdfLoadingEffect(): void {
        effect(() => {
            if (!this.iframeReady()) {
                return;
            }

            const pdfUrl = this.pdfUrl();
            if (pdfUrl) {
                untracked(() => this.loadPdf(pdfUrl, this.initialPage() ?? 1));
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

    private setupLanguageSyncEffect(): void {
        effect(() => {
            this.languageChange();
            if (this.iframeReady()) {
                this.postMessageToIframe('languageChange', { languageKey: this.getCurrentLanguageKey() });
            }
        });
    }

    private setupViewerModeSyncEffect(): void {
        effect(() => {
            if (this.iframeReady()) {
                this.postMessageToIframe('viewerModeChange', { viewerMode: this.isFullscreen() ? 'fullscreen' : 'embedded' });
            }
        });
    }

    private setupFullscreenFocusEffect(): void {
        effect(() => {
            if (!this.isFullscreen()) {
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

    /**
     * Switches the embedded PDF viewer into fullscreen mode.
     */
    openFullscreen(): void {
        if (!this.pdfUrl() || this.isFullscreen()) {
            return;
        }
        this.applyFullscreenLayering();
        this.isFullscreen.set(true);
        this.isFullscreenChange.emit(true);
    }

    /**
     * Leaves fullscreen mode and restores layering changes applied for drawer contexts.
     */
    closeFullscreen(): void {
        if (!this.isFullscreen()) {
            return;
        }
        this.isFullscreen.set(false);
        this.isFullscreenChange.emit(false);
        this.resetFullscreenLayering();
    }

    /**
     * Handles Escape key events coming from the fullscreen overlay.
     */
    onFullscreenEscape(event: Event): void {
        if (!this.isFullscreen()) {
            return;
        }

        event.preventDefault();
        event.stopPropagation();
        this.closeFullscreen();
    }

    @HostListener('window:message', ['$event'])
    protected onWindowMessage(event: MessageEvent<IframeMessage>): void {
        this.handleIframeMessage(event);
    }

    /** Handles iframe messages and ignores messages from invalid origins/sources. */
    private handleIframeMessage(event: MessageEvent<IframeMessage>): void {
        if (event.origin !== window.location.origin) {
            return;
        }

        const iframe = this.pdfIframe()?.nativeElement;
        if (!iframe?.contentWindow || event.source !== iframe.contentWindow) {
            return;
        }

        if (!event.data || typeof event.data !== 'object') {
            return;
        }

        const { type, data } = event.data;

        if (type === 'ready') {
            const wasReady = this.iframeReady();
            this.iframeReady.set(true);
            // Drag & drop in instructor view can re-initialize the iframe; when this happens, the current PDF must be loaded again.
            if (wasReady) {
                this.reloadCurrentPdf();
            }
            return;
        }

        if (type === 'pageChange' && typeof data?.page === 'number' && Number.isInteger(data.page) && data.page > 0) {
            this.currentPage.set(data.page);
            return;
        }

        if (type === 'openFullscreen') {
            this.openFullscreen();
            return;
        }

        if (type === 'closeFullscreen') {
            this.closeFullscreen();
            return;
        }

        if (type === 'pageRendered') {
            this.isLoading.set(false);
            this.pageRendered.emit({ pdfUrl: data?.url ?? this.pdfUrl() ?? '' });
            return;
        }

        if (type === 'pdfLoadError') {
            this.isLoading.set(false);
            this.loadError.emit({ pdfUrl: data?.url ?? this.pdfUrl() ?? '' });
            return;
        }

        if (type === 'download') {
            this.downloadRequested.emit();
        }
    }

    private loadPdf(url: string, page: number): void {
        const isDarkMode = untracked(() => this.themeService.currentTheme() === Theme.DARK);
        const languageKey = untracked(() => this.getCurrentLanguageKey());
        this.isLoading.set(true);
        this.currentPage.set(page);

        this.postMessageToIframe('loadPDF', {
            url,
            initialPage: page,
            isDarkMode,
            languageKey,
            viewerMode: this.isFullscreen() ? 'fullscreen' : 'embedded',
        });
    }

    private getCurrentLanguageKey(): string {
        return this.translateService.getCurrentLang() || 'en';
    }

    private reloadCurrentPdf(): void {
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

    private applyFullscreenLayering(): void {
        const drawerContent = this.hostElementRef.nativeElement.closest('.mat-drawer-content') as HTMLElement | null;
        if (!drawerContent) {
            return;
        }

        this.drawerContentElement = drawerContent;
        this.originalDrawerContentZIndex = drawerContent.style.zIndex;
        drawerContent.style.zIndex = '4000';
    }

    private resetFullscreenLayering(): void {
        if (!this.drawerContentElement) {
            return;
        }

        this.drawerContentElement.style.zIndex = this.originalDrawerContentZIndex ?? '';
        this.drawerContentElement = undefined;
        this.originalDrawerContentZIndex = undefined;
    }
}
