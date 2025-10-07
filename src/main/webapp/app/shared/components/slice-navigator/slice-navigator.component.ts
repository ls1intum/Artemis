import { Component, computed, effect, input, output, signal } from '@angular/core';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';

export interface PaginationConfig {
    pageSize: number;
    initialPage?: number;
}

export interface PageChangeEvent {
    page: number;
    pageSize: number;
    direction: 'next' | 'previous';
}

@Component({
    selector: 'jhi-slice-navigator',
    imports: [ButtonComponent],
    templateUrl: './slice-navigator.component.html',
})
export class SliceNavigatorComponent {
    protected readonly faChevronLeft = faChevronLeft;
    protected readonly faChevronRight = faChevronRight;
    protected readonly buttonType = ButtonType.SECONDARY;
    protected readonly buttonSize = ButtonSize.SMALL;

    config = input.required<PaginationConfig>();
    hasMoreItems = input<boolean>(true);
    isLoading = input<boolean>(false);

    pageChange = output<PageChangeEvent>();

    currentPage = signal(1);
    itemsPerPage = signal(ITEMS_PER_PAGE);

    canGoPrevious = computed(() => this.currentPage() > 1);
    canGoNext = computed(() => this.hasMoreItems() && !this.isLoading());
    previousDisabled = computed(() => !this.canGoPrevious() || this.isLoading());
    nextDisabled = computed(() => !this.canGoNext() || this.isLoading());

    constructor() {
        effect(() => {
            const initialConfig = this.config();
            this.itemsPerPage.set(initialConfig.pageSize);

            if (initialConfig.initialPage) {
                this.currentPage.set(initialConfig.initialPage);
            }
        });
    }

    previousPage(): void {
        if (this.canGoPrevious()) {
            const newPage = this.currentPage() - 1;
            this.currentPage.set(newPage);
            this.emitPageChange('previous');
        }
    }

    nextPage(): void {
        if (this.canGoNext()) {
            const newPage = this.currentPage() + 1;
            this.currentPage.set(newPage);

            this.emitPageChange('next');
        }
    }

    private emitPageChange(direction: 'next' | 'previous'): void {
        this.pageChange.emit({
            page: this.currentPage(),
            pageSize: this.itemsPerPage(),
            direction,
        });
    }
}
