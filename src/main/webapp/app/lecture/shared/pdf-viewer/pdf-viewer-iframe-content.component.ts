import {
    ChangeDetectionStrategy,
    Component,
    ElementRef,
    HostListener,
    Injector,
    OnDestroy,
    OnInit,
    ViewEncapsulation,
    afterNextRender,
    effect,
    inject,
    signal,
    viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { NgxExtendedPdfViewerModule, PDFNotificationService, type PdfLoadedEvent, pdfDefaultOptions } from 'ngx-extended-pdf-viewer';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    faChevronDown,
    faChevronUp,
    faDownload,
    faExpand,
    faMagnifyingGlass,
    faMagnifyingGlassMinus,
    faMagnifyingGlassPlus,
    faTimes,
    faXmark,
} from '@fortawesome/free-solid-svg-icons';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumber, InputNumberModule } from 'primeng/inputnumber';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import type { IframeMessage, IframeMessageData, IframeMessageType } from './pdf-viewer-iframe.types';

// Configure pdf.js default options
pdfDefaultOptions.assetsFolder = 'assets/ngx-extended-pdf-viewer';

interface PDFViewerApplication {
    eventBus?: {
        dispatch: (eventName: string, data?: unknown) => void;
    };
    appConfig?: {
        mainContainer?: HTMLElement;
    };
    pdfViewer?: {
        container?: HTMLElement;
        currentScale?: number;
    };
}

interface FindMatchesCount {
    current: number;
    total: number;
}

const TOOLBAR_COMPACT_LEVELS = 5;
const TOOLBAR_COMPACT_CLASS_PREFIX = 'artemis-pdf-toolbar__center--compact-';

/** Iframe PDF viewer content with toolbar, search, page navigation, and zoom. */
@Component({
    selector: 'jhi-pdf-viewer-iframe-content',
    standalone: true,
    imports: [NgxExtendedPdfViewerModule, FaIconComponent, ArtemisTranslatePipe, FormsModule, InputTextModule, InputNumberModule],
    templateUrl: './pdf-viewer-iframe-content.component.html',
    styleUrls: ['./pdf-viewer-iframe-content.component.scss'],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfViewerIframeContentComponent implements OnInit, OnDestroy {
    private readonly pdfNotificationService = inject(PDFNotificationService);
    private readonly translateService = inject(TranslateService);
    private readonly injector = inject(Injector);

    readonly pdfUrl = signal('');
    readonly currentPage = signal(1);
    readonly totalPages = signal(0);
    readonly isDarkMode = signal(false);
    readonly isFullscreenMode = signal(false);
    readonly pageInputValue = signal<number | undefined>(1);

    readonly pageInputElement = viewChild<InputNumber>('pageInput');
    readonly searchNextButtonElement = viewChild<ElementRef<HTMLButtonElement>>('searchNextButton');
    readonly toolbarCenterElement = viewChild<ElementRef<HTMLDivElement>>('toolbarCenter');

    protected readonly faMagnifyingGlassMinus = faMagnifyingGlassMinus;
    protected readonly faMagnifyingGlassPlus = faMagnifyingGlassPlus;
    protected readonly faMagnifyingGlass = faMagnifyingGlass;
    protected readonly faChevronUp = faChevronUp;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faTimes = faTimes;
    protected readonly faDownload = faDownload;
    protected readonly faExpand = faExpand;
    protected readonly faXmark = faXmark;

    protected searchQuery = signal('');
    protected searchMatchesCount = signal<FindMatchesCount | undefined>(undefined);

    private resizeObserver?: ResizeObserver;
    private firstPageRenderedPosted = false;

    constructor() {
        effect(() => {
            this.searchQuery();
            this.totalPages();
            this.isFullscreenMode();
            this.scheduleToolbarCompressionUpdate();
        });
    }

    ngOnInit(): void {
        this.postMessageToParent('ready', {});
        this.scheduleToolbarCompressionUpdate(() => {
            this.initializeToolbarResizeObserver();
            this.updateToolbarCompressionLevel();
        });
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
    }

    @HostListener('window:message', ['$event'])
    protected onWindowMessage(event: MessageEvent<IframeMessage>): void {
        this.handleParentMessage(event);
    }

    @HostListener('window:keydown', ['$event'])
    protected onKeyDown(event: KeyboardEvent): void {
        if (event.key !== 'Escape') {
            return;
        }

        if (this.shouldBlurSearchOnEscape(event.target)) {
            event.preventDefault();
            this.clearSearch();
            const activeElement = document.activeElement;
            if (activeElement instanceof HTMLElement) {
                activeElement.blur();
            }
            return;
        }

        if (this.shouldCancelPageNavigationOnEscape(event.target)) {
            event.preventDefault();
            this.cancelPageNavigation();
            const activeElement = document.activeElement;
            if (activeElement instanceof HTMLElement) {
                activeElement.blur();
            }
            return;
        }

        if (this.isFullscreenMode()) {
            event.preventDefault();
            this.postMessageToParent('closeFullscreen', {});
        }
    }

    /** Handles valid parent messages and updates URL, page, and theme state. */
    private handleParentMessage(event: MessageEvent<IframeMessage>): void {
        if (event.origin !== window.location.origin || event.source !== window.parent) {
            return;
        }

        if (!event.data || typeof event.data !== 'object') {
            return;
        }

        const { type, data } = event.data;

        switch (type) {
            case 'loadPDF':
                this.firstPageRenderedPosted = false;
                if (typeof data?.url === 'string' && data.url.length > 0) {
                    const urlChanged = data.url !== this.pdfUrl();
                    if (urlChanged) {
                        this.pdfUrl.set(data.url);
                        this.totalPages.set(0);
                        this.searchQuery.set('');
                        this.searchMatchesCount.set(undefined);
                    }
                    if (data.initialPage !== undefined && Number.isInteger(data.initialPage) && data.initialPage > 0) {
                        this.setCurrentPage(data.initialPage);
                    } else if (urlChanged) {
                        this.setCurrentPage(1);
                    }
                }
                this.isFullscreenMode.set(data?.viewerMode === 'fullscreen');
                this.updateDarkMode(data?.isDarkMode);
                this.updateLanguage(data?.languageKey);
                break;
            case 'viewerModeChange':
                this.isFullscreenMode.set(data?.viewerMode === 'fullscreen');
                break;
            case 'themeChange':
                this.updateDarkMode(data?.isDarkMode);
                break;
            case 'languageChange':
                this.updateLanguage(data?.languageKey);
                break;
        }
    }

    onPageChange(page: number): void {
        this.setCurrentPage(page);
        this.postMessageToParent('pageChange', { page });
    }

    onPageRendered(): void {
        if (this.firstPageRenderedPosted) {
            return;
        }

        this.firstPageRenderedPosted = true;
        this.postMessageToParent('pageRendered', { url: this.pdfUrl() });
    }

    onPdfLoaded(event: PdfLoadedEvent): void {
        this.updateTotalPages(event.pagesCount);
    }

    onPdfLoadingFailed(): void {
        this.postMessageToParent('pdfLoadError', { url: this.pdfUrl() });
    }

    onFindMatchesCountUpdate(event: FindMatchesCount): void {
        this.searchMatchesCount.set(event);
    }

    zoomIn(): void {
        this.dispatchZoomEvent('zoomin');
    }

    zoomOut(): void {
        this.dispatchZoomEvent('zoomout');
    }

    private dispatchZoomEvent(eventName: 'zoomin' | 'zoomout'): void {
        const pdfViewerApplication = this.getPdfViewerApplication();
        const eventBus = pdfViewerApplication?.eventBus;
        if (!eventBus) {
            return;
        }

        const pdfViewer = pdfViewerApplication.pdfViewer;
        const container = pdfViewerApplication.appConfig?.mainContainer ?? pdfViewer?.container ?? document.getElementById('viewerContainer');
        const currentScale = pdfViewer?.currentScale;

        if (!container || !currentScale || !container.clientWidth || !container.clientHeight) {
            eventBus.dispatch(eventName);
            return;
        }

        const centerX = container.scrollLeft + container.clientWidth / 2;
        const centerY = container.scrollTop + container.clientHeight / 2;

        eventBus.dispatch(eventName);
        requestAnimationFrame(() => {
            const nextScale = pdfViewer?.currentScale ?? currentScale;
            const scaleFactor = nextScale / currentScale;
            container.scrollLeft = Math.max(0, centerX * scaleFactor - container.clientWidth / 2);
            container.scrollTop = Math.max(0, centerY * scaleFactor - container.clientHeight / 2);
        });
    }

    private postMessageToParent(type: IframeMessageType, data: IframeMessageData): void {
        window.parent.postMessage({ type, data }, window.location.origin);
    }

    private updateTotalPages(totalPages: number): void {
        this.totalPages.set(totalPages);

        const currentPage = this.currentPage();
        if (currentPage < 1 || currentPage > totalPages) {
            this.setCurrentPage(1);
        }
    }

    protected performSearch(query: string): void {
        if (!query.trim()) {
            this.clearSearch();
            return;
        }

        this.searchQuery.set(query);
        this.dispatchFindCommand('find', query, true, false);
    }

    protected onSearchInputEnter(event: Event): void {
        event.preventDefault();
        this.search(false);

        afterNextRender(
            () => {
                const nextButton = this.searchNextButtonElement()?.nativeElement;
                if (nextButton && !nextButton.disabled) {
                    nextButton.focus();
                }
            },
            { injector: this.injector },
        );
    }

    protected search(findPrevious: boolean): void {
        const query = this.searchQuery();
        if (!query) {
            return;
        }

        this.dispatchFindCommand('again', query, true, findPrevious);
    }

    protected clearSearch(): void {
        this.searchQuery.set('');
        this.searchMatchesCount.set(undefined);
        this.dispatchFindCommand('find', '', false, false);
    }

    protected onPageInputEnter(event: Event): void {
        event.preventDefault();
        this.pageInputElement()?.input?.nativeElement?.blur();
    }

    protected confirmPageNavigation(): void {
        const value = this.pageInputValue();
        const totalPages = this.totalPages();
        const previousPage = this.currentPage();

        if (totalPages === 0) {
            return;
        }

        if (value === undefined || !Number.isInteger(value) || value < 1 || value > totalPages) {
            const fallbackPage = previousPage > 0 && previousPage <= totalPages ? previousPage : 1;
            this.pageInputValue.set(fallbackPage);
        } else {
            this.setCurrentPage(value);
        }
    }

    protected cancelPageNavigation(): void {
        this.pageInputValue.set(this.currentPage());
    }

    protected onPageInputFocus(): void {
        window.setTimeout(() => {
            this.pageInputElement()?.input?.nativeElement?.select();
        }, 0);
    }

    protected triggerDownload(): void {
        this.postMessageToParent('download', {});
    }

    protected requestFullscreen(): void {
        this.postMessageToParent('openFullscreen', {});
    }

    protected closeFullscreen(): void {
        this.postMessageToParent('closeFullscreen', {});
    }

    private getPdfViewerApplication(): PDFViewerApplication | undefined {
        return this.pdfNotificationService.onPDFJSInitSignal() as unknown as PDFViewerApplication | undefined;
    }

    private dispatchFindCommand(type: 'find' | 'again', query: string, highlightAll: boolean, findPrevious: boolean): void {
        const eventBus = this.getPdfViewerApplication()?.eventBus;
        if (!eventBus) {
            return;
        }

        eventBus.dispatch('find', {
            type,
            query,
            caseSensitive: false,
            entireWord: false,
            highlightAll,
            findPrevious,
        });
    }

    /** Keeps page state and page input value in sync. */
    private setCurrentPage(page: number): void {
        this.currentPage.set(page);
        this.pageInputValue.set(page);
    }

    /** Updates dark mode only when the value is valid and has changed. */
    private updateDarkMode(isDarkMode?: boolean): void {
        if (typeof isDarkMode === 'boolean' && isDarkMode !== this.isDarkMode()) {
            this.isDarkMode.set(isDarkMode);
        }
    }

    private updateLanguage(languageKey?: string): void {
        if (!languageKey || languageKey === this.translateService.getCurrentLang()) {
            return;
        }
        this.translateService.use(languageKey);
    }

    private initializeToolbarResizeObserver(): void {
        const toolbarCenterElement = this.toolbarCenterElement()?.nativeElement;
        if (!toolbarCenterElement || typeof ResizeObserver === 'undefined') {
            return;
        }

        this.resizeObserver?.disconnect();
        this.resizeObserver = new ResizeObserver(() => {
            this.updateToolbarCompressionLevel();
        });
        this.resizeObserver.observe(toolbarCenterElement);
    }

    private updateToolbarCompressionLevel(): void {
        const toolbarCenterElement = this.toolbarCenterElement()?.nativeElement;
        if (!toolbarCenterElement || toolbarCenterElement.clientWidth === 0) {
            return;
        }

        for (let compressionLevel = 0; compressionLevel <= TOOLBAR_COMPACT_LEVELS; compressionLevel += 1) {
            this.applyToolbarCompressionLevel(toolbarCenterElement, compressionLevel);
            if (toolbarCenterElement.scrollWidth <= toolbarCenterElement.clientWidth || compressionLevel === TOOLBAR_COMPACT_LEVELS) {
                break;
            }
        }
    }

    private applyToolbarCompressionLevel(toolbarCenterElement: HTMLElement, compressionLevel: number): void {
        for (let level = 1; level <= TOOLBAR_COMPACT_LEVELS; level += 1) {
            toolbarCenterElement.classList.toggle(`${TOOLBAR_COMPACT_CLASS_PREFIX}${level}`, compressionLevel >= level);
        }
    }

    private shouldBlurSearchOnEscape(eventTarget: EventTarget | null): boolean {
        return this.isWithinSearchToolbar(eventTarget) || this.isWithinSearchToolbar(document.activeElement);
    }

    private shouldCancelPageNavigationOnEscape(eventTarget: EventTarget | null): boolean {
        return this.isWithinPageNavigation(eventTarget) || this.isWithinPageNavigation(document.activeElement);
    }

    private isWithinSearchToolbar(node: EventTarget | null): boolean {
        return node instanceof Element && !!node.closest('.artemis-pdf-toolbar__search');
    }

    private isWithinPageNavigation(node: EventTarget | null): boolean {
        return node instanceof Element && !!node.closest('.artemis-pdf-toolbar__page-navigation');
    }

    private scheduleToolbarCompressionUpdate(callback: () => void = () => this.updateToolbarCompressionLevel()): void {
        afterNextRender(callback, { injector: this.injector });
    }
}
