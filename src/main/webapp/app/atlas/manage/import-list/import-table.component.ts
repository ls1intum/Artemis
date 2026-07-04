import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { BaseEntity } from 'app/foundation/model/base-entity';
import { PagingService } from 'app/exercise/services/paging.service';
import { SearchResult, SearchTermPageableSearch, SortingOrder } from 'app/foundation/pagination/pageable-table';
import { lastValueFrom } from 'rxjs';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { faSort, faSortDown, faSortUp, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { BaseApiHttpService } from 'app/foundation/service/base-api-http.service';
import { NgbTypeaheadModule } from '@ng-bootstrap/ng-bootstrap';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

/**
 * An abstract component intended for cases where a resource needs to be imported from one course into another.
 *
 * @template T generic class parameter of the entity that gets imported
 */
export type Column<T extends BaseEntity> = {
    name: string;
    getProperty(entity: T): string | undefined;
};

@Component({
    selector: 'jhi-import-table',
    imports: [PaginatorModule, ArtemisTranslatePipe, TranslateDirective, FontAwesomeModule, FormsModule, NgbTypeaheadModule, CommonModule],
    templateUrl: './import-table.component.html',
    styleUrl: './import-table.component.scss',
})
export class ImportTableComponent<T extends BaseEntity> {
    protected readonly SortingOrder = SortingOrder;

    protected readonly faSort = faSort;
    protected readonly faSortUp = faSortUp;
    protected readonly faSortDown = faSortDown;
    protected readonly faSpinner = faSpinner;

    private readonly alertService = inject(AlertService);
    private readonly pagingService = inject(PagingService);

    columns = input.required<Column<T>[]>();
    readonly columnBaseTranslationKey = input.required<string>();
    disabledIds = input<number[]>([]);
    readonly numberOfColumns = computed(() => this.columns().length + 2);

    readonly onRowSelection = output<T>();

    readonly isLoading = signal<boolean>(false);
    private readonly searchResult = signal<SearchResult<T> | undefined>(undefined);
    readonly resultsOnPage = computed(() => this.searchResult()?.resultsOnPage ?? []);

    private readonly DEFAULT_SORTING_ORDER = SortingOrder.ASCENDING;
    private readonly PAGE_SIZE = 10;

    readonly searchTerm = signal<string>('');
    readonly sortingOrder = signal<SortingOrder>(this.DEFAULT_SORTING_ORDER);
    readonly sortedColumn = signal<string>('ID');
    readonly page = signal<number>(1);
    readonly pageSize = signal<number>(this.PAGE_SIZE).asReadonly();
    readonly collectionSize = computed(() => {
        const numberOfPages = this.searchResult()?.numberOfPages ?? 1;
        return numberOfPages === 1 ? this.resultsOnPage().length : numberOfPages * this.pageSize();
    });

    constructor() {
        effect(() => {
            untracked(async () => await this.loadData());
        });
    }

    /**
     * Runs the current paginated search and applies its result to the table.
     *
     * Several searches can be in flight simultaneously (the initial unfiltered load plus debounced loads
     * issued while typing) and their responses may return out of order. A response is therefore only applied
     * when the search state it was issued with still matches the current state; otherwise a newer request has
     * superseded it and the stale response is discarded so the table never shows results for an old query.
     */
    private async loadData(): Promise<void> {
        try {
            this.isLoading.set(true);
            const searchState = <SearchTermPageableSearch>{
                searchTerm: this.searchTerm(),
                page: this.page(),
                sortedColumn: this.sortedColumn(),
                sortingOrder: this.sortingOrder(),
                pageSize: this.pageSize(),
            };
            const result = await lastValueFrom(this.pagingService.search(searchState));
            // A newer request has superseded this one; discard its (now stale) response.
            if (
                searchState.searchTerm !== this.searchTerm() ||
                searchState.page !== this.page() ||
                searchState.sortedColumn !== this.sortedColumn() ||
                searchState.sortingOrder !== this.sortingOrder()
            ) {
                return;
            }
            const filteredResults = this.filterSearchResult(result);
            this.searchResult.set(filteredResults);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading.set(false);
        }
    }

    /*
     * Debounce the data load to prevent unnecessary requests while typing.
     */
    private readonly debouncedDataLoad = BaseApiHttpService.debounce(this.loadData.bind(this), 300);

    private filterSearchResult(searchResults: SearchResult<T>): SearchResult<T> {
        return <SearchResult<T>>{
            ...searchResults,
            resultsOnPage: searchResults.resultsOnPage?.filter((entity) => !this.disabledIds().includes(entity.id!)),
        };
    }

    protected async setSortedColumn(sortedColumn: string): Promise<void> {
        const sortingOrder = this.sortingOrder() === SortingOrder.ASCENDING ? SortingOrder.DESCENDING : SortingOrder.ASCENDING;
        this.sortingOrder.set(this.sortedColumn() === sortedColumn ? sortingOrder : this.DEFAULT_SORTING_ORDER);
        this.sortedColumn.set(sortedColumn);
        await this.loadData();
    }

    protected async setPage(page: number): Promise<void> {
        this.page.set(page);
        await this.loadData();
    }

    /** PrimeNG paginator page change (0-indexed) converted to the 1-indexed page used here. */
    protected onPageChange(event: PaginatorState): void {
        void this.setPage((event.page ?? 0) + 1);
    }

    protected search(): void {
        this.debouncedDataLoad();
    }

    protected selectRow(item: T): void {
        this.onRowSelection.emit(item);
    }
}
