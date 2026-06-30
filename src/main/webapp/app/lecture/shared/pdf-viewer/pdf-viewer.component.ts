import { HttpClient } from '@angular/common/http';
import {
    ChangeDetectionStrategy,
    Component,
    DestroyRef,
    ElementRef,
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
    viewChildren,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import type { Dayjs } from 'dayjs/esm';
import { firstValueFrom } from 'rxjs';
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
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { PdfEngineService } from 'app/core/pdf/pdf-engine.service';
import type { PdfDocumentObject, Rect, SearchResult } from '@embedpdf/models';

/** A single rendered page: an object-URL image plus its CSS display size at the current scale. */
interface RenderedPage {
    index: number;
    url: string;
    width: number;
    height: number;
}

/** A search-match rectangle in unscaled PDF page points, tagged with whether it belongs to the active match. */
interface PageHighlight {
    rect: Rect;
    active: boolean;
}

const ZOOM_STEP = 1.25;
const MIN_SCALE = 0.2;
const MAX_SCALE = 6;
const PAGE_GAP = 8;

/**
 * Single-instance PDF viewer rendered directly with the EmbedPDF PDFium engine (no iframe, no pdf.js).
 * It renders every page to an image via the worker engine, overlays search highlights, and exposes a custom
 * toolbar (search, zoom, page navigation, download, fullscreen). The public input/output contract is preserved
 * so consumers (attachment-video-unit, lecture-unit, course-lecture-details) are unaffected.
 *
 * Text selection is intentionally not yet ported: it requires the heavier EmbedPDF selection/interaction-manager
 * stack and is the weakest area of the engine; it is tracked as a follow-up.
 */
@Component({
    selector: 'jhi-pdf-viewer',
    standalone: true,
    imports: [FormsModule, FaIconComponent, InputTextModule, InputNumberModule, ArtemisDatePipe, ArtemisTranslatePipe, TranslateDirective],
    templateUrl: './pdf-viewer.component.html',
    styleUrls: ['./pdf-viewer.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfViewerComponent {
    private static instanceCounter = 0;
    private readonly docId = `pdf-viewer-${++PdfViewerComponent.instanceCounter}`;

    // Inputs
    pdfUrl = input<string | undefined>(undefined);
    uploadDate = input<Dayjs | undefined>(undefined);
    version = input<number | undefined>(undefined);
    initialPage = input<number | undefined>(undefined);

    // Outputs (unchanged public contract)
    readonly pageRendered = output<{ pdfUrl: string }>();
    readonly loadError = output<{ pdfUrl: string }>();
    readonly downloadRequested = output<void>();
    readonly isFullscreenChange = output<boolean>();
    readonly currentPageChange = output<number>();

    readonly fullscreenWindow = viewChild<ElementRef<HTMLDivElement>>('fullscreenWindow');
    readonly pagesContainer = viewChild<ElementRef<HTMLDivElement>>('pagesContainer');
    readonly toolbarCenter = viewChild<ElementRef<HTMLDivElement>>('toolbarCenter');
    readonly pageInputElement = viewChild<InputNumber>('pageInput');
    readonly pageElements = viewChildren<ElementRef<HTMLDivElement>>('pageRef');

    protected readonly isLoading = signal(false);
    protected readonly isFullscreen = signal(false);
    protected readonly renderedPages = signal<RenderedPage[]>([]);
    protected readonly totalPages = signal(0);
    protected readonly pageInputValue = signal<number | undefined>(1);
    protected readonly searchQuery = signal('');
    protected readonly searchMatchesCount = signal<{ current: number; total: number } | undefined>(undefined);

    private readonly scale = signal(1);
    private readonly currentPage = signal(1);
    readonly currentPageSignal = this.currentPage.asReadonly();
    private readonly doc = signal<PdfDocumentObject | undefined>(undefined);
    private readonly searchResults = signal<SearchResult[]>([]);
    private readonly activeResultIndex = signal(0);

    // Icons
    protected readonly faMagnifyingGlass = faMagnifyingGlass;
    protected readonly faMagnifyingGlassMinus = faMagnifyingGlassMinus;
    protected readonly faMagnifyingGlassPlus = faMagnifyingGlassPlus;
    protected readonly faChevronUp = faChevronUp;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faTimes = faTimes;
    protected readonly faDownload = faDownload;
    protected readonly faExpand = faExpand;
    protected readonly faXmark = faXmark;

    private readonly themeService = inject(ThemeService);
    private readonly http = inject(HttpClient);
    private readonly pdfEngineService = inject(PdfEngineService);
    private readonly destroyRef = inject(DestroyRef);
    private readonly injector = inject(Injector);
    private readonly hostElementRef = inject(ElementRef<HTMLElement>);

    protected readonly isDarkMode = computed(() => this.themeService.currentTheme() === Theme.DARK);
    protected readonly scaleValue = this.scale.asReadonly();
    protected readonly effectiveUploadDate = computed(() => this.uploadDate());
    protected readonly effectiveVersion = computed(() => this.version());

    /** Per-page search highlights (in PDF points); the template multiplies by the current scale. */
    protected readonly pageHighlights = computed<Map<number, PageHighlight[]>>(() => {
        const map = new Map<number, PageHighlight[]>();
        const results = this.searchResults();
        const active = this.activeResultIndex();
        results.forEach((result, resultIndex) => {
            const list = map.get(result.pageIndex) ?? [];
            for (const rect of result.rects) {
                list.push({ rect, active: resultIndex === active });
            }
            map.set(result.pageIndex, list);
        });
        return map;
    });

    private renderToken = 0;
    private lastFullscreenState?: boolean;
    private intersectionObserver?: IntersectionObserver;
    private drawerContentElement?: HTMLElement;
    private originalDrawerContentZIndex?: string;

    constructor() {
        this.destroyRef.onDestroy(() => this.cleanup());

        // (Re)load whenever the PDF URL changes.
        effect(() => {
            const url = this.pdfUrl();
            if (url) {
                untracked(() => void this.loadPdf(url));
            }
        });

        // Re-fit and re-render only when fullscreen actually toggles (the available width changes substantially).
        // doc() is read untracked so a document load does not trigger an extra render here.
        effect(() => {
            const fullscreen = this.isFullscreen();
            untracked(() => {
                if (this.lastFullscreenState !== undefined && this.lastFullscreenState !== fullscreen && this.doc()) {
                    this.refitAndRender();
                }
                this.lastFullscreenState = fullscreen;
            });
        });

        // Re-observe page elements for current-page tracking whenever the rendered pages change.
        effect(() => {
            this.pageElements();
            untracked(() => this.observePages());
        });

        // Recompute the toolbar compression level when relevant signals change.
        effect(() => {
            this.searchQuery();
            this.totalPages();
            this.isFullscreen();
            untracked(() => this.scheduleToolbarCompressionUpdate());
        });
    }

    private async loadPdf(url: string): Promise<void> {
        this.isLoading.set(true);
        this.resetState();
        const token = ++this.renderToken;
        try {
            const blob = await firstValueFrom(this.http.get(url, { responseType: 'blob' }));
            const arrayBuffer = await blob.arrayBuffer();
            const engine = await this.pdfEngineService.getEngine();
            if (token !== this.renderToken) {
                return;
            }
            const doc = await engine.openDocumentBuffer({ id: this.docId, content: arrayBuffer }).toPromise();
            if (token !== this.renderToken) {
                await engine
                    .closeDocument(doc)
                    .toPromise()
                    .catch(() => {});
                return;
            }
            this.doc.set(doc);
            this.totalPages.set(doc.pageCount);
            this.scale.set(this.computeFitWidthScale(doc));
            await this.renderAllPages(token);

            const initialPage = this.initialPage() ?? 1;
            this.setCurrentPage(initialPage);
            if (initialPage > 1) {
                afterNextRender(() => this.goToPage(initialPage), { injector: this.injector });
            }

            this.isLoading.set(false);
            this.pageRendered.emit({ pdfUrl: url });
        } catch {
            this.isLoading.set(false);
            this.loadError.emit({ pdfUrl: url });
        }
    }

    /** Renders every page to an object-URL image at the current scale (× devicePixelRatio for crispness). */
    private async renderAllPages(token: number): Promise<void> {
        const doc = this.doc();
        if (!doc) {
            return;
        }
        const engine = await this.pdfEngineService.getEngine();
        const scale = this.scale();
        const dpr = window.devicePixelRatio || 1;
        const previous = this.renderedPages();
        const pages: RenderedPage[] = [];
        for (const page of doc.pages) {
            const blob = await engine.renderPage(doc, page, { scaleFactor: scale * dpr }).toPromise();
            if (token !== this.renderToken) {
                URL.revokeObjectURL(URL.createObjectURL(blob));
                return;
            }
            pages.push({
                index: page.index,
                url: URL.createObjectURL(blob),
                width: page.size.width * scale,
                height: page.size.height * scale,
            });
        }
        this.renderedPages.set(pages);
        previous.forEach((p) => URL.revokeObjectURL(p.url));
    }

    private refitAndRender(): void {
        const doc = this.doc();
        if (!doc) {
            return;
        }
        // Recompute fit-to-width only when not manually zoomed away from a fit; simplest robust behavior: refit.
        this.scale.set(this.computeFitWidthScale(doc));
        void this.renderAllPages(++this.renderToken);
    }

    private computeFitWidthScale(doc: PdfDocumentObject): number {
        const container = this.pagesContainer()?.nativeElement;
        const available = (container?.clientWidth ?? 0) - 2 * PAGE_GAP;
        const maxPageWidth = Math.max(1, ...doc.pages.map((p) => p.size.width));
        if (available <= 0) {
            return 1;
        }
        return Math.min(MAX_SCALE, Math.max(MIN_SCALE, available / maxPageWidth));
    }

    protected zoomIn(): void {
        this.applyZoom(this.scale() * ZOOM_STEP);
    }

    protected zoomOut(): void {
        this.applyZoom(this.scale() / ZOOM_STEP);
    }

    private applyZoom(nextScale: number): void {
        const clamped = Math.min(MAX_SCALE, Math.max(MIN_SCALE, nextScale));
        if (clamped === this.scale()) {
            return;
        }
        this.scale.set(clamped);
        void this.renderAllPages(++this.renderToken);
    }

    // ---- Page navigation -------------------------------------------------------------------------------------

    private observePages(): void {
        this.intersectionObserver?.disconnect();
        const elements = this.pageElements();
        const container = this.pagesContainer()?.nativeElement;
        if (elements.length === 0 || !container) {
            return;
        }
        const ratios = new Map<number, number>();
        // Disconnected on teardown via destroyRef.onDestroy(() => this.cleanup()) and before each re-observe above.
        // eslint-disable-next-line localRules/enforce-cleanup-on-destroy
        this.intersectionObserver = new IntersectionObserver(
            (entries) => {
                for (const entry of entries) {
                    const index = Number((entry.target as HTMLElement).dataset['pageIndex']);
                    ratios.set(index, entry.intersectionRatio);
                }
                let bestIndex = 0;
                let bestRatio = -1;
                ratios.forEach((ratio, index) => {
                    if (ratio > bestRatio) {
                        bestRatio = ratio;
                        bestIndex = index;
                    }
                });
                if (bestRatio > 0) {
                    this.setCurrentPage(bestIndex + 1);
                }
            },
            { root: container, threshold: [0.1, 0.25, 0.5, 0.75, 1] },
        );
        elements.forEach((ref) => this.intersectionObserver!.observe(ref.nativeElement));
    }

    private setCurrentPage(page: number): void {
        if (page < 1 || page > this.totalPages() || page === this.currentPage()) {
            return;
        }
        this.currentPage.set(page);
        this.pageInputValue.set(page);
        this.currentPageChange.emit(page);
    }

    getCurrentPage(): number {
        return this.currentPage();
    }

    goToPage(page: number): void {
        if (!Number.isInteger(page) || page < 1 || page > this.totalPages()) {
            return;
        }
        const element = this.pageElements().find((ref) => Number(ref.nativeElement.dataset['pageIndex']) === page - 1);
        element?.nativeElement.scrollIntoView({ block: 'start', behavior: 'smooth' });
        this.currentPage.set(page);
        this.pageInputValue.set(page);
    }

    protected onPageInputEnter(event: Event): void {
        event.preventDefault();
        this.pageInputElement()?.input?.nativeElement?.blur();
    }

    protected onPageInputFocus(): void {
        window.setTimeout(() => this.pageInputElement()?.input?.nativeElement?.select(), 0);
    }

    protected confirmPageNavigation(): void {
        const value = this.pageInputValue();
        const total = this.totalPages();
        if (total === 0) {
            return;
        }
        if (value === undefined || !Number.isInteger(value) || value < 1 || value > total) {
            this.pageInputValue.set(this.currentPage());
        } else {
            this.goToPage(value);
        }
    }

    // ---- Search ----------------------------------------------------------------------------------------------

    protected async performSearch(query: string): Promise<void> {
        this.searchQuery.set(query);
        const doc = this.doc();
        if (!query.trim() || !doc) {
            this.clearSearchResults();
            return;
        }
        const engine = await this.pdfEngineService.getEngine();
        const result = await engine.searchAllPages(doc, query).toPromise();
        this.searchResults.set(result.results);
        this.activeResultIndex.set(0);
        this.searchMatchesCount.set(result.total > 0 ? { current: 1, total: result.total } : undefined);
        if (result.total > 0) {
            this.scrollToActiveResult();
        }
    }

    protected onSearchInputEnter(event: Event): void {
        event.preventDefault();
        this.search(false);
    }

    /** Moves to the next (findPrevious=false) or previous (true) search match, wrapping around. */
    protected search(findPrevious: boolean): void {
        const total = this.searchResults().length;
        if (total === 0) {
            return;
        }
        const next = (this.activeResultIndex() + (findPrevious ? -1 : 1) + total) % total;
        this.activeResultIndex.set(next);
        this.searchMatchesCount.set({ current: next + 1, total });
        this.scrollToActiveResult();
    }

    protected clearSearch(): void {
        this.searchQuery.set('');
        this.clearSearchResults();
    }

    private clearSearchResults(): void {
        this.searchResults.set([]);
        this.activeResultIndex.set(0);
        this.searchMatchesCount.set(undefined);
    }

    private scrollToActiveResult(): void {
        const result = this.searchResults()[this.activeResultIndex()];
        if (result) {
            this.goToPage(result.pageIndex + 1);
        }
    }

    // ---- Download / fullscreen -------------------------------------------------------------------------------

    protected triggerDownload(): void {
        this.downloadRequested.emit();
    }

    openFullscreen(): void {
        if (!this.pdfUrl() || this.isFullscreen()) {
            return;
        }
        this.applyFullscreenLayering();
        this.isFullscreen.set(true);
        this.isFullscreenChange.emit(true);
        afterNextRender(() => this.fullscreenWindow()?.nativeElement.focus(), { injector: this.injector });
    }

    closeFullscreen(): void {
        if (!this.isFullscreen()) {
            return;
        }
        this.isFullscreen.set(false);
        this.isFullscreenChange.emit(false);
        this.resetFullscreenLayering();
    }

    protected requestFullscreen(): void {
        this.openFullscreen();
    }

    onFullscreenEscape(event: Event): void {
        if (!this.isFullscreen()) {
            return;
        }
        event.preventDefault();
        event.stopPropagation();
        this.closeFullscreen();
    }

    private applyFullscreenLayering(): void {
        const drawerContent = (this.hostElementRef.nativeElement.closest('.layout-content') ??
            this.hostElementRef.nativeElement.closest('.mat-drawer-content')) as HTMLElement | null;
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

    // ---- Toolbar compression (ported from the previous iframe content component) -----------------------------

    private static readonly TOOLBAR_COMPACT_LEVELS = 5;
    private static readonly TOOLBAR_COMPACT_CLASS_PREFIX = 'artemis-pdf-toolbar__center--compact-';

    private scheduleToolbarCompressionUpdate(): void {
        afterNextRender(() => this.updateToolbarCompressionLevel(), { injector: this.injector });
    }

    private updateToolbarCompressionLevel(): void {
        const element = this.toolbarCenter()?.nativeElement;
        if (!element || element.clientWidth === 0) {
            return;
        }
        for (let level = 0; level <= PdfViewerComponent.TOOLBAR_COMPACT_LEVELS; level += 1) {
            for (let applied = 1; applied <= PdfViewerComponent.TOOLBAR_COMPACT_LEVELS; applied += 1) {
                element.classList.toggle(`${PdfViewerComponent.TOOLBAR_COMPACT_CLASS_PREFIX}${applied}`, level >= applied);
            }
            if (element.scrollWidth <= element.clientWidth || level === PdfViewerComponent.TOOLBAR_COMPACT_LEVELS) {
                break;
            }
        }
    }

    // ---- Lifecycle -------------------------------------------------------------------------------------------

    private resetState(): void {
        this.renderedPages().forEach((p) => URL.revokeObjectURL(p.url));
        this.renderedPages.set([]);
        this.clearSearchResults();
        this.searchQuery.set('');
        this.totalPages.set(0);
        this.currentPage.set(1);
        this.pageInputValue.set(1);
        const previousDoc = this.doc();
        this.doc.set(undefined);
        if (previousDoc) {
            this.pdfEngineService
                .getEngine()
                .then((engine) => engine.closeDocument(previousDoc).toPromise())
                .catch(() => {});
        }
    }

    private cleanup(): void {
        this.intersectionObserver?.disconnect();
        this.renderToken++;
        this.renderedPages().forEach((p) => URL.revokeObjectURL(p.url));
        const doc = this.doc();
        if (doc) {
            this.pdfEngineService
                .getEngine()
                .then((engine) => engine.closeDocument(doc).toPromise())
                .catch(() => {});
        }
        this.resetFullscreenLayering();
    }
}
