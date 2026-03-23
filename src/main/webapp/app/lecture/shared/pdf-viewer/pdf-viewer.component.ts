import { Component, ViewEncapsulation, computed, inject, input, signal } from '@angular/core';
import type { Dayjs } from 'dayjs/esm';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgxExtendedPdfViewerModule, PDFNotificationService, type PagesLoadedEvent, pdfDefaultOptions } from 'ngx-extended-pdf-viewer';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faMagnifyingGlassMinus, faMagnifyingGlassPlus } from '@fortawesome/free-solid-svg-icons';

pdfDefaultOptions.assetsFolder = 'assets/ngx-extended-pdf-viewer';

@Component({
    selector: 'jhi-lecture-pdf-viewer',
    standalone: true,
    imports: [NgxExtendedPdfViewerModule, ArtemisDatePipe, TranslateDirective, FaIconComponent],
    templateUrl: './pdf-viewer.component.html',
    styleUrls: ['./pdf-viewer.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class PdfViewerComponent {
    private readonly pdfNotificationService = inject(PDFNotificationService);

    pdfUrl = input.required<string>();
    uploadDate = input<Dayjs | undefined>(undefined);
    version = input<number | undefined>(undefined);
    initialPage = input<number | undefined>(undefined);
    readonly resolvedPage = computed(() => this.initialPage() ?? 1);
    readonly currentPage = signal<number | undefined>(undefined);
    readonly totalPages = signal(0);
    readonly displayPage = computed(() => this.currentPage() ?? this.resolvedPage());

    protected readonly faMagnifyingGlassMinus = faMagnifyingGlassMinus;
    protected readonly faMagnifyingGlassPlus = faMagnifyingGlassPlus;

    onPageChange(page: number): void {
        this.currentPage.set(page);
    }

    onPagesLoaded(event: PagesLoadedEvent): void {
        this.totalPages.set(event.pagesCount ?? 0);
    }

    zoomIn(): void {
        this.dispatchZoomEvent('zoomin');
    }

    zoomOut(): void {
        this.dispatchZoomEvent('zoomout');
    }

    private dispatchZoomEvent(eventName: 'zoomin' | 'zoomout'): void {
        const pdfViewerApplication = this.pdfNotificationService.onPDFJSInitSignal();
        pdfViewerApplication?.eventBus?.dispatch(eventName);
    }
}
