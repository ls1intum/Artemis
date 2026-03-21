import { Component, ElementRef, OnDestroy, computed, effect, input, signal, viewChild } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import type { Dayjs } from 'dayjs/esm';
import { TranslateModule } from '@ngx-translate/core';
import { NgxExtendedPdfViewerModule } from 'ngx-extended-pdf-viewer';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faExclamationTriangle, faRotateLeft, faSearchMinus, faSearchPlus } from '@fortawesome/free-solid-svg-icons';
import { ButtonDirective } from 'primeng/button';

@Component({
    selector: 'jhi-pdf-viewer',
    standalone: true,
    imports: [FontAwesomeModule, TranslateModule, ArtemisDatePipe, ArtemisTranslatePipe, TranslateDirective, ButtonDirective, NgxExtendedPdfViewerModule],
    templateUrl: './pdf-viewer.component.html',
    styleUrls: ['./pdf-viewer.component.scss'],
})
export class PdfViewerComponent implements OnDestroy {
    pdfUrl = input.required<string>();
    uploadDate = input<Dayjs | undefined>(undefined);
    version = input<number | undefined>(undefined);
    initialPage = input<number | undefined>(undefined);
    viewerHost = viewChild<ElementRef<HTMLElement>>('viewerHost');
    totalPages = signal<number>(0);
    currentPage = signal<number>(1);
    isLoading = signal<boolean>(true);
    error = signal<string | undefined>(undefined);
    zoomLevel = signal<number>(1.0);
    fitWidthZoomFactor = signal<number>(1.0);
    private readonly isPinchZoomActive = signal<boolean>(false);
    readonly viewerZoomBinding = computed(() => {
        const zoom = this.zoomLevel() * (this.fitWidthZoomFactor() || 1);
        return this.isPinchZoomActive() ? undefined : Number.isFinite(zoom) ? Number((zoom * 100).toFixed(2)) : 100;
    });
    protected readonly faSearchMinus = faSearchMinus;
    protected readonly faSearchPlus = faSearchPlus;
    protected readonly faRotateLeft = faRotateLeft;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    private pendingZoomAnchor?: { centerXRatio: number; centerYRatio: number };
    private lastViewerHostWidth = 0;
    private fitWidthTimeoutId?: number;
    private lastViewerZoomFactor = 0;
    private basePageWidth = 0;
    private isApplyingZoom = false;
    private zoomApplyTimeoutId?: number;
    private pinchZoomTimeoutId?: number;
    private readonly handleBrowserZoomKeys = (event: KeyboardEvent): void => {
        if ((event.ctrlKey || event.metaKey) && (['+', '=', '-', '0'].includes(event.key) || ['Equal', 'Minus', 'NumpadAdd', 'NumpadSubtract', 'Numpad0'].includes(event.code))) {
            event.stopImmediatePropagation();
            event.stopPropagation();
        }
    };
    private readonly handlePinchZoomWheel = (event: WheelEvent): void => {
        if (event.ctrlKey || event.metaKey) this.markPinchZoomActive();
    };
    private readonly handlePinchZoomGesture = (): void => this.markPinchZoomActive();

    constructor() {
        if (typeof window !== 'undefined') {
            window.addEventListener('keydown', this.handleBrowserZoomKeys, { capture: true });
        }
        effect(() => this.pdfUrl() && this.resetPdfState());
        effect(() => this.tryApplyInitialPage());
        effect((onCleanup) => {
            const host = this.viewerHost()?.nativeElement;
            if (!host) {
                return;
            }

            this.lastViewerHostWidth = host.clientWidth;
            const resizeObserver = new ResizeObserver((entries) => this.handleViewerHostResize(entries));
            resizeObserver.observe(host);
            const gestureEvents = ['gesturestart', 'gesturechange', 'gestureend'] as const;
            gestureEvents.forEach((event) => host.addEventListener(event, this.handlePinchZoomGesture, { capture: true }));
            host.addEventListener('wheel', this.handlePinchZoomWheel, { capture: true, passive: true });

            onCleanup(() => {
                resizeObserver.disconnect();
                gestureEvents.forEach((event) => host.removeEventListener(event, this.handlePinchZoomGesture, { capture: true }));
                host.removeEventListener('wheel', this.handlePinchZoomWheel, { capture: true });
            });
        });
    }

    ngOnDestroy(): void {
        if (typeof window !== 'undefined') window.removeEventListener('keydown', this.handleBrowserZoomKeys, { capture: true });
        clearTimeout(this.fitWidthTimeoutId);
        clearTimeout(this.zoomApplyTimeoutId);
        clearTimeout(this.pinchZoomTimeoutId);
    }

    onPdfLoadingStarts(): void {
        this.resetPdfState();
        this.lastViewerZoomFactor = 0;
        this.basePageWidth = 0;
        this.fitWidthZoomFactor.set(1.0);
    }

    onPdfLoaded(event: { pagesCount?: number; numPages?: number; pages?: number }): void {
        this.totalPages.set(event?.pagesCount ?? event?.numPages ?? event?.pages ?? 0);
        this.isLoading.set(false);
        this.error.set(undefined);
        this.scheduleFitWidthUpdate();
        this.tryApplyInitialPage();
    }

    onPdfLoadingFailed(): void {
        this.error.set('error');
        this.isLoading.set(false);
        this.totalPages.set(0);
    }

    onPageChange(pageNumber: number): void {
        if (Number.isFinite(pageNumber) && pageNumber > 0 && pageNumber !== this.currentPage()) this.currentPage.set(pageNumber);
    }

    onZoomFactorChange(zoomFactor: number): void {
        if (!Number.isFinite(zoomFactor)) return;
        this.lastViewerZoomFactor = zoomFactor;
        if (!this.basePageWidth && this.ensureBasePageWidth(zoomFactor)) {
            this.updateFitWidthZoomFactor();
        }
        const fitWidthFactor = this.fitWidthZoomFactor() || 1;
        const expectedViewerZoom = this.zoomLevel() * fitWidthFactor;
        if (this.isApplyingZoom) return this.finishZoomApplyIfSettled(expectedViewerZoom, zoomFactor);
        if (Math.abs(zoomFactor - expectedViewerZoom) > 0.01) {
            const relativeZoom = zoomFactor / fitWidthFactor;
            const clamped = Math.max(0.5, Math.min(3.0, relativeZoom));
            if (Math.abs(clamped - this.zoomLevel()) > 0.001) {
                this.zoomLevel.set(clamped);
            }
        }
        this.applyZoomAnchor();
    }

    private tryApplyInitialPage(): void {
        const initialPage = this.initialPage();
        if (initialPage === undefined || this.isLoading() || this.totalPages() <= 0) return;
        const targetPage = Math.max(1, Math.min(this.totalPages(), initialPage));
        if (targetPage !== this.currentPage()) this.currentPage.set(targetPage);
    }

    zoomIn(): void {
        this.applyUserZoom(this.zoomLevel() + 0.25);
    }

    zoomOut(): void {
        this.applyUserZoom(this.zoomLevel() - 0.25);
    }

    resetZoom(): void {
        this.applyUserZoom(1.0, true);
    }

    private captureZoomAnchor(): void {
        const container = this.getViewerContainer();
        if (!container) return;

        const { scrollLeft, scrollTop, scrollWidth, scrollHeight, clientWidth, clientHeight } = container;
        if (scrollWidth <= 0 || scrollHeight <= 0) return;

        const centerXRatio = (scrollLeft + clientWidth / 2) / scrollWidth;
        const centerYRatio = (scrollTop + clientHeight / 2) / scrollHeight;
        this.pendingZoomAnchor = {
            centerXRatio: Number.isFinite(centerXRatio) ? centerXRatio : 0.5,
            centerYRatio: Number.isFinite(centerYRatio) ? centerYRatio : 0.5,
        };
    }

    private applyZoomAnchor(): void {
        const anchor = this.pendingZoomAnchor;
        const container = this.getViewerContainer();
        if (!anchor || !container) return;
        this.pendingZoomAnchor = undefined;
        requestAnimationFrame(() => {
            const { scrollWidth, scrollHeight, clientWidth, clientHeight } = container;
            if (!(scrollWidth > 0 && scrollHeight > 0)) return;

            const newScrollLeft = Math.max(0, Math.min(scrollWidth - clientWidth, anchor.centerXRatio * scrollWidth - clientWidth / 2));
            const newScrollTop = Math.max(0, Math.min(scrollHeight - clientHeight, anchor.centerYRatio * scrollHeight - clientHeight / 2));

            if (!Number.isFinite(newScrollLeft) || !Number.isFinite(newScrollTop)) return;

            container.scrollLeft = newScrollLeft;
            container.scrollTop = newScrollTop;
        });
    }

    private getViewerContainer(): HTMLElement | null {
        const host = this.viewerHost()?.nativeElement;
        return host?.querySelector<HTMLElement>('#viewerContainer') ?? host?.querySelector<HTMLElement>('.pdfViewer')?.parentElement ?? null;
    }

    private applyUserZoom(nextZoom: number, force = false): void {
        this.captureZoomAnchor();
        if (this.pendingZoomAnchor) this.beginZoomApply();
        const clamped = Math.max(0.5, Math.min(3.0, nextZoom));
        if (force || clamped !== this.zoomLevel()) {
            this.zoomLevel.set(clamped);
        }
    }

    private handleViewerHostResize(entries: ResizeObserverEntry[]): void {
        const newWidth = entries[0]?.contentRect?.width ?? 0;
        if (!newWidth || newWidth <= 0) return;

        if (!this.lastViewerHostWidth) {
            this.lastViewerHostWidth = newWidth;
            return;
        }

        if (Math.abs(newWidth - this.lastViewerHostWidth) < 1) return;

        this.lastViewerHostWidth = newWidth;
        if (!this.pendingZoomAnchor) {
            this.captureZoomAnchor();
        }
        this.scheduleFitWidthUpdate();
    }

    private scheduleFitWidthUpdate(): void {
        clearTimeout(this.fitWidthTimeoutId);

        this.fitWidthTimeoutId = window.setTimeout(() => {
            this.updateFitWidthZoomFactor();
        }, 120);
    }

    private updateFitWidthZoomFactor(): void {
        const container = this.getViewerContainer();
        if (!container || !this.ensureBasePageWidth()) return;
        const nextFitWidth = container.clientWidth / this.basePageWidth;
        if (!Number.isFinite(nextFitWidth) || nextFitWidth <= 0) return;
        if (Math.abs(nextFitWidth - this.fitWidthZoomFactor()) < 0.01) {
            return this.applyZoomAnchor();
        }
        if (this.pendingZoomAnchor) this.beginZoomApply();
        this.fitWidthZoomFactor.set(nextFitWidth);
    }

    private ensureBasePageWidth(zoomFactor?: number): boolean {
        if (this.basePageWidth > 0) {
            return true;
        }
        const effectiveZoom = zoomFactor && zoomFactor > 0 ? zoomFactor : this.lastViewerZoomFactor;
        if (!Number.isFinite(effectiveZoom) || effectiveZoom <= 0) return false;
        const pageWidth = this.viewerHost()?.nativeElement.querySelector<HTMLElement>('.page')?.getBoundingClientRect().width ?? 0;
        if (!pageWidth || pageWidth <= 0) return false;
        const basePageWidth = pageWidth / effectiveZoom;
        if (!Number.isFinite(basePageWidth) || basePageWidth <= 0) return false;

        this.basePageWidth = basePageWidth;
        return this.basePageWidth > 0;
    }

    private beginZoomApply(): void {
        this.isApplyingZoom = true;
        clearTimeout(this.zoomApplyTimeoutId);

        this.zoomApplyTimeoutId = window.setTimeout(() => this.finishZoomApply(), 300);
    }

    private finishZoomApplyIfSettled(expectedZoom: number, actualZoom: number): void {
        if (Math.abs(actualZoom - expectedZoom) > 0.01) return;
        clearTimeout(this.zoomApplyTimeoutId);
        this.finishZoomApply();
    }

    private markPinchZoomActive(): void {
        this.isPinchZoomActive.set(true);
        clearTimeout(this.pinchZoomTimeoutId);
        this.pinchZoomTimeoutId = window.setTimeout(() => {
            this.isPinchZoomActive.set(false);
        }, 150);
    }

    private finishZoomApply(): void {
        this.isApplyingZoom = false;
        this.applyZoomAnchor();
    }

    private resetPdfState(): void {
        this.isLoading.set(true);
        this.error.set(undefined);
        this.totalPages.set(0);
        this.currentPage.set(1);
    }
}
