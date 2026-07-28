import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faAngleLeft, faAngleRight, faAnglesLeft, faAnglesRight } from '@fortawesome/free-solid-svg-icons';
import { TumUiTranslatePipe } from '../i18n/tum-ui-translate.pipe';

const PAGE_LINK_SIZE = 5;
const NAV_BUTTON_CLASSES =
    'inline-flex h-[35px] w-[35px] shrink-0 cursor-pointer appearance-none items-center justify-center rounded-full border-0 bg-transparent text-sm text-tum-ui-muted transition-colors hover:bg-tum-ui-surface-100 dark:hover:bg-tum-ui-surface-800 disabled:pointer-events-none disabled:opacity-50';

/**
 * Paginator for {@link TumUiTableComponent}.
 * Signal-based, PrimeNG-free. Matches PrimeNG's p-paginator: a centered row with a "Showing X to Y of Z"
 * report, circular first/prev/page-number/next/last controls, and a rows-per-page select. Pages are 0-based.
 */
@Component({
    selector: 'tum-ui-paginator',
    templateUrl: './tum-ui-paginator.component.html',
    imports: [FaIconComponent, TumUiTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiPaginatorComponent {
    readonly totalRecords = input(0);
    readonly page = input(0);
    readonly pageSize = input(50);
    readonly pageSizeOptions = input<number[]>([10, 20, 50, 100, 200]);
    readonly disabled = input(false);
    /**
     * Show the "Showing X to Y of Z" report. Default true (the smart-table look). Set false when replacing a
     * bare PrimeNG `p-paginator` that rendered navigation only — or where a separate `jhi-item-count` already
     * shows the count — so the migrated view stays visually identical.
     */
    readonly showCurrentPageReport = input(true);
    /** Show the rows-per-page `<select>`. Default true. Set false when the original `p-paginator` had no `rowsPerPageOptions`. */
    readonly showRowsPerPage = input(true);

    readonly pageChange = output<number>();
    readonly pageSizeChange = output<number>();

    protected readonly faAnglesLeft = faAnglesLeft;
    protected readonly faAngleLeft = faAngleLeft;
    protected readonly faAngleRight = faAngleRight;
    protected readonly faAnglesRight = faAnglesRight;

    protected readonly navButtonClasses = NAV_BUTTON_CLASSES;
    protected readonly selectedPageClasses = NAV_BUTTON_CLASSES.replace('bg-transparent', 'bg-tum-ui-primary/15').replace('text-tum-ui-muted', 'font-semibold text-tum-ui-primary');

    protected readonly totalPages = computed(() => Math.max(1, Math.ceil(this.totalRecords() / Math.max(1, this.pageSize()))));
    protected readonly clampedPage = computed(() => Math.min(Math.max(0, this.page()), this.totalPages() - 1));
    protected readonly isFirst = computed(() => this.clampedPage() <= 0);
    protected readonly isLast = computed(() => this.clampedPage() >= this.totalPages() - 1);
    protected readonly rangeBegin = computed(() => (this.totalRecords() === 0 ? 0 : this.clampedPage() * this.pageSize() + 1));
    protected readonly rangeEnd = computed(() => Math.min(this.totalRecords(), (this.clampedPage() + 1) * this.pageSize()));

    /** 0-based page indices to render as page-number buttons, windowed around the current page (like PrimeNG). */
    protected readonly visiblePages = computed(() => {
        const total = this.totalPages();
        const size = Math.min(PAGE_LINK_SIZE, total);
        let start = Math.max(0, this.clampedPage() - Math.floor(size / 2));
        const end = Math.min(total, start + size);
        start = Math.max(0, end - size);
        return Array.from({ length: end - start }, (_, i) => start + i);
    });

    protected goToPage(target: number): void {
        if (!this.disabled() && target !== this.page() && target >= 0 && target < this.totalPages()) {
            this.pageChange.emit(target);
        }
    }

    protected goToFirst(): void {
        if (!this.disabled() && !this.isFirst()) {
            this.pageChange.emit(0);
        }
    }

    protected goToPrevious(): void {
        if (!this.disabled() && !this.isFirst()) {
            this.pageChange.emit(this.clampedPage() - 1);
        }
    }

    protected goToNext(): void {
        if (!this.disabled() && !this.isLast()) {
            this.pageChange.emit(this.clampedPage() + 1);
        }
    }

    protected goToLast(): void {
        if (!this.disabled() && !this.isLast()) {
            this.pageChange.emit(this.totalPages() - 1);
        }
    }

    protected onPageSizeChange(value: string): void {
        this.pageSizeChange.emit(Number(value));
    }
}
