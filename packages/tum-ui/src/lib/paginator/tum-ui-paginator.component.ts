import { Directionality } from '@angular/cdk/bidi';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faAngleLeft, faAngleRight, faAnglesLeft, faAnglesRight } from '@fortawesome/free-solid-svg-icons';
import { TumUiTranslatePipe } from '../i18n/tum-ui-translate.pipe';

const PAGE_LINK_SIZE = 5;
const NAV_BUTTON_CLASSES =
    'tum:inline-flex tum:h-9 tum:w-9 tum:shrink-0 tum:cursor-pointer tum:appearance-none tum:items-center tum:justify-center tum:rounded-full tum:border-0 tum:bg-transparent tum:text-sm tum:text-muted tum:transition-colors tum:hover:bg-hover-background tum:disabled:pointer-events-none tum:disabled:opacity-50';

/** Controlled paginator using zero-based page indexes. */
@Component({
    selector: 'tum-ui-paginator',
    templateUrl: './tum-ui-paginator.component.html',
    imports: [FaIconComponent, FormsModule, TumUiTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiPaginatorComponent {
    private readonly directionality = inject(Directionality);
    private readonly direction = signal(this.directionality.value);
    private readonly destroyRef = inject(DestroyRef);

    readonly ariaLabel = input('Pagination');
    /** Total records in the consumer-owned result set. */
    readonly totalRecords = input(0);
    /** Zero-based active page index. */
    readonly page = input(0);
    /** Controlled number of records per page. */
    readonly pageSize = input(50);
    readonly pageSizeOptions = input<number[]>([10, 20, 50, 100, 200]);
    readonly disabled = input(false);

    readonly showCurrentPageReport = input(true);

    readonly showRowsPerPage = input(true);

    /** Requests a zero-based page without mutating `page`. */
    readonly pageChange = output<number>();
    /** Requests a page size without mutating `pageSize`. */
    readonly pageSizeChange = output<number>();

    protected readonly firstPageIcon = computed(() => (this.direction() === 'rtl' ? faAnglesRight : faAnglesLeft));
    protected readonly previousPageIcon = computed(() => (this.direction() === 'rtl' ? faAngleRight : faAngleLeft));
    protected readonly nextPageIcon = computed(() => (this.direction() === 'rtl' ? faAngleLeft : faAngleRight));
    protected readonly lastPageIcon = computed(() => (this.direction() === 'rtl' ? faAnglesLeft : faAnglesRight));

    protected readonly navButtonClasses = NAV_BUTTON_CLASSES;
    protected readonly selectedPageClasses = NAV_BUTTON_CLASSES.replace('tum:bg-transparent', 'tum:bg-primary/15').replace('tum:text-muted', 'tum:font-semibold tum:text-accent');

    protected readonly totalPages = computed(() => Math.max(1, Math.ceil(this.totalRecords() / Math.max(1, this.pageSize()))));
    protected readonly clampedPage = computed(() => Math.min(Math.max(0, this.page()), this.totalPages() - 1));
    protected readonly isFirst = computed(() => this.clampedPage() <= 0);
    protected readonly isLast = computed(() => this.clampedPage() >= this.totalPages() - 1);
    protected readonly rangeBegin = computed(() => (this.totalRecords() === 0 ? 0 : this.clampedPage() * this.pageSize() + 1));
    protected readonly rangeEnd = computed(() => Math.min(this.totalRecords(), (this.clampedPage() + 1) * this.pageSize()));

    protected readonly visiblePages = computed(() => {
        const total = this.totalPages();
        const size = Math.min(PAGE_LINK_SIZE, total);
        let start = Math.max(0, this.clampedPage() - Math.floor(size / 2));
        const end = Math.min(total, start + size);
        start = Math.max(0, end - size);
        return Array.from({ length: end - start }, (_, i) => start + i);
    });

    constructor() {
        const directionChanges = this.directionality.change.subscribe((direction) => this.direction.set(direction));
        this.destroyRef.onDestroy(() => directionChanges.unsubscribe());
    }

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

    protected onPageSizeChange(value: number): void {
        this.pageSizeChange.emit(value);
    }
}
