import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { BaseEntity } from 'app/shared/model/base-entity';
import { PagingService } from 'app/exercises/shared/manage/paging.service';
import { PageableSearch, SearchResult, SearchTermPageableSearch, SortingOrder } from 'app/shared/table/pageable-table';
import { lastValueFrom } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { faSort, faSortDown, faSortUp, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';

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
    selector: 'jhi-import-list',
    standalone: true,
    imports: [ArtemisSharedCommonModule],
    templateUrl: './import-list.component.html',
    styleUrl: './import-list.component.scss',
})
export class ImportListComponent<T extends BaseEntity> {
    protected readonly SortingOrder = SortingOrder;

    protected readonly faSort = faSort;
    protected readonly faSortUp = faSortUp;
    protected readonly faSortDown = faSortDown;
    protected readonly faSpinner = faSpinner;

    private readonly alertService = inject(AlertService);
    private readonly pagingService = inject(PagingService);

    readonly columns = input.required<Column<T>[]>();
    readonly entityName = input.required<string>();
    readonly disabledIds = input<number[]>([]);

    readonly onRowSelection = output<T>();

    readonly isLoading = signal<boolean>(false);
    private readonly searchResult = signal<SearchResult<T> | undefined>(undefined);
    readonly resultsOnPage = computed(() => this.searchResult()?.resultsOnPage ?? []);

    readonly searchTerm = signal<string>('');
    private readonly defaultSortingOrder = SortingOrder.ASCENDING;
    private readonly searchState = signal<PageableSearch>({
        page: 1,
        pageSize: 10,
        sortingOrder: this.defaultSortingOrder,
        sortedColumn: 'ID',
    });
    readonly sortingOrder = computed(() => this.searchState().sortingOrder!);
    readonly sortedColumn = computed(() => this.searchState().sortedColumn!);
    readonly page = computed(() => this.searchState().page!);
    readonly pageSize = computed(() => this.searchState().pageSize!);
    readonly collectionSize = computed(() => {
        const numberOfPages = this.searchResult()?.numberOfPages ?? 1;
        return numberOfPages === 1 ? this.resultsOnPage().length : numberOfPages * this.pageSize();
    });

    constructor() {
        const debouncedDataLoad = BaseApiHttpService.debounce(this.loadData.bind(this), 300);

        effect(() => {
            // Debounce loading data whenever search term changes
            const searchTerm = this.searchTerm();
            untracked(async () => {
                if (this.searchResult()) {
                    debouncedDataLoad(this.searchState(), searchTerm);
                }
            });
        });
        effect(() => {
            // Load data whenever search state changes
            const searchState = this.searchState();
            untracked(async () => await this.loadData(searchState, this.searchTerm()));
        });
    }

    private async loadData(searchState: PageableSearch, searchTerm: string): Promise<void> {
        try {
            this.isLoading.set(true);
            const completeSearchState = <SearchTermPageableSearch>{
                ...searchState,
                searchTerm,
            };
            const result = await lastValueFrom(this.pagingService.search(completeSearchState));
            const filteredResults = this.filterSearchResult(result);
            this.searchResult.set(filteredResults);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading.set(false);
        }
    }

    private filterSearchResult(searchResults: SearchResult<T>): SearchResult<T> {
        return <SearchResult<T>>{
            ...searchResults,
            resultsOnPage: searchResults.resultsOnPage?.filter((entity) => !this.disabledIds().includes(entity.id!)),
        };
    }

    protected setSortedColumn(sortedColumn: string): void {
        this.searchState.update((state) => {
            let sortingOrder = state.sortingOrder === SortingOrder.ASCENDING ? SortingOrder.DESCENDING : SortingOrder.ASCENDING;
            sortingOrder = state.sortedColumn === sortedColumn ? sortingOrder : this.defaultSortingOrder;
            return {
                ...state,
                sortedColumn: sortedColumn,
                sortingOrder: sortingOrder,
            };
        });
    }

    protected setPage(page: number): void {
        this.searchState.update((state) => ({ ...state, page: page }));
    }

    protected selectRow(item: T): void {
        this.onRowSelection.emit(item);
    }
}
