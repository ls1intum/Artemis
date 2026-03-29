import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, OnInit, ViewEncapsulation, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgxExtendedPdfViewerModule, PDFNotificationService, type PagesLoadedEvent, pdfDefaultOptions } from 'ngx-extended-pdf-viewer';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faChevronDown, faChevronUp, faDownload, faMagnifyingGlass, faMagnifyingGlassMinus, faMagnifyingGlassPlus, faTimes } from '@fortawesome/free-solid-svg-icons';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumber, InputNumberModule } from 'primeng/inputnumber';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import type { IframeMessage, IframeMessageData, IframeMessageType } from './pdf-viewer-iframe.types';

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

type FindCommandType = 'find' | 'again';
const PAGE_INPUT_BLUR_DELAY_MS = 200;

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
export class PdfViewerIframeContentComponent implements OnInit {
    private readonly pdfNotificationService = inject(PDFNotificationService);
    private readonly destroyRef = inject(DestroyRef);

    readonly pdfUrl = signal<string>('');
    readonly currentPage = signal<number>(1);
    readonly totalPages = signal<number>(0);
    readonly isDarkMode = signal<boolean>(false);
    readonly pageInputValue = signal<number | undefined>(1);
    readonly isPageInputFocused = signal<boolean>(false);

    readonly pageInputElement = viewChild<InputNumber>('pageInput');
    readonly searchNextButtonElement = viewChild<ElementRef<HTMLButtonElement>>('searchNextButton');

    protected readonly faMagnifyingGlassMinus = faMagnifyingGlassMinus;
    protected readonly faMagnifyingGlassPlus = faMagnifyingGlassPlus;
    protected readonly faMagnifyingGlass = faMagnifyingGlass;
    protected readonly faChevronUp = faChevronUp;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faTimes = faTimes;
    protected readonly faCheck = faCheck;
    protected readonly faDownload = faDownload;

    protected searchQuery = signal<string>('');
    protected searchMatchesCount = signal<FindMatchesCount | undefined>(undefined);

    ngOnInit(): void {
        window.addEventListener('message', this.handleParentMessage as EventListener);
        this.destroyRef.onDestroy(() => {
            window.removeEventListener('message', this.handleParentMessage as EventListener);
        });

        this.postMessageToParent('ready', {});
    }

    /** Handles valid parent messages and updates URL, page, and theme state. */
    private readonly handleParentMessage = (event: MessageEvent<IframeMessage>): void => {
        if (event.origin !== window.location.origin || event.source !== window.parent) {
            return;
        }

        if (!event.data || typeof event.data !== 'object') {
            return;
        }

        const { type, data } = event.data;

        switch (type) {
            case 'loadPDF':
                if (typeof data?.url === 'string' && data.url.length > 0) {
                    const urlChanged = data.url !== this.pdfUrl();
                    if (urlChanged) {
                        this.pdfUrl.set(data.url);
                    }
                    if (data.initialPage !== undefined && Number.isInteger(data.initialPage) && data.initialPage > 0) {
                        this.setCurrentPage(data.initialPage);
                    } else if (urlChanged) {
                        this.setCurrentPage(1);
                    }
                }
                this.updateDarkMode(data?.isDarkMode);
                break;
            case 'themeChange':
                this.updateDarkMode(data?.isDarkMode);
                break;
        }
    };

    onPageChange(page: number): void {
        this.setCurrentPage(page);
        this.postMessageToParent('pageChange', { page });
    }

    onPagesLoaded(event: PagesLoadedEvent): void {
        const totalPages = event.pagesCount ?? 0;
        this.totalPages.set(totalPages);

        const currentPage = this.currentPage();
        if (currentPage < 1 || currentPage > totalPages) {
            this.setCurrentPage(1);
        }
        this.postMessageToParent('pagesLoaded', { pagesCount: totalPages, url: this.pdfUrl() });
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

    protected performSearch(query: string): void {
        if (!query.trim()) {
            this.clearSearch();
            return;
        }

        this.searchQuery.set(query);
        this.dispatchFindCommand({ type: 'find', query, highlightAll: true, findPrevious: false });
    }

    protected onSearchInputEnter(event: Event): void {
        event.preventDefault();
        this.searchNext();

        setTimeout(() => {
            const nextButton = this.searchNextButtonElement()?.nativeElement;
            if (nextButton && !nextButton.disabled) {
                nextButton.focus();
            }
        });
    }

    protected searchNext(): void {
        const query = this.searchQuery();
        if (!query) {
            return;
        }

        this.dispatchFindCommand({ type: 'again', query, highlightAll: true, findPrevious: false });
    }

    protected searchPrevious(): void {
        const query = this.searchQuery();
        if (!query) {
            return;
        }

        this.dispatchFindCommand({ type: 'again', query, highlightAll: true, findPrevious: true });
    }

    protected clearSearch(): void {
        this.searchQuery.set('');
        this.searchMatchesCount.set(undefined);
        this.dispatchFindCommand({ type: 'find', query: '', highlightAll: false, findPrevious: false });
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
            this.currentPage.set(value);
        }

        this.blurPageInput();
        this.isPageInputFocused.set(false);
    }

    protected onPageInputFocus(): void {
        this.isPageInputFocused.set(true);
        const inputNumber = this.pageInputElement();
        if (inputNumber?.input?.nativeElement) {
            setTimeout(() => {
                inputNumber.input.nativeElement.select();
            });
        }
    }

    protected onPageInputBlur(): void {
        setTimeout(() => {
            this.isPageInputFocused.set(false);
        }, PAGE_INPUT_BLUR_DELAY_MS);
    }

    protected triggerDownload(): void {
        this.postMessageToParent('download', {});
    }

    private getPdfViewerApplication(): PDFViewerApplication | undefined {
        return this.pdfNotificationService.onPDFJSInitSignal() as unknown as PDFViewerApplication | undefined;
    }

    private dispatchFindCommand(params: { type: FindCommandType; query: string; highlightAll: boolean; findPrevious: boolean }): void {
        const eventBus = this.getPdfViewerApplication()?.eventBus;
        if (!eventBus) {
            return;
        }

        eventBus.dispatch('find', {
            type: params.type,
            query: params.query,
            caseSensitive: false,
            entireWord: false,
            highlightAll: params.highlightAll,
            findPrevious: params.findPrevious,
        });
    }

    private blurPageInput(): void {
        const inputNumber = this.pageInputElement();
        if (inputNumber?.input?.nativeElement) {
            inputNumber.input.nativeElement.blur();
        }
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
}
