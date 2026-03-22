import { Component, computed, input, signal } from '@angular/core';
import type { Dayjs } from 'dayjs/esm';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgxExtendedPdfViewerModule, type PagesLoadedEvent, pdfDefaultOptions } from 'ngx-extended-pdf-viewer';

pdfDefaultOptions.assetsFolder = 'assets/ngx-extended-pdf-viewer';

@Component({
    selector: 'jhi-pdf-viewer',
    standalone: true,
    imports: [NgxExtendedPdfViewerModule, TranslateModule, ArtemisDatePipe, TranslateDirective],
    templateUrl: './pdf-viewer.component.html',
    styleUrls: ['./pdf-viewer.component.scss'],
})
export class PdfViewerComponent {
    pdfUrl = input.required<string>();
    uploadDate = input<Dayjs | undefined>(undefined);
    version = input<number | undefined>(undefined);
    initialPage = input<number | undefined>(undefined);
    readonly resolvedPage = computed(() => this.initialPage() ?? 1);
    readonly currentPage = signal<number | undefined>(undefined);
    readonly totalPages = signal(0);
    readonly displayPage = computed(() => this.currentPage() ?? this.resolvedPage());

    onPageChange(page: number): void {
        this.currentPage.set(page);
    }

    onPagesLoaded(event: PagesLoadedEvent): void {
        this.totalPages.set(event.pagesCount ?? 0);
    }
}
