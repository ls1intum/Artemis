import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { BaseEntity } from 'app/shared/model/base-entity';
import { PagingService } from 'app/exercises/shared/manage/paging.service';
import { SearchResult, SearchTermPageableSearch, SortingOrder } from 'app/shared/table/pageable-table';
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
    readonly columnBaseTranslationKey = input.required<string>();
    readonly disabledIds = input<number[]>([]);

    readonly onRowSelection = output<T>();

    readonly isLoading = signal<boolean>(false);
    private readonly searchResult = signal<SearchResult<T> | undefined>(undefined);
    readonly resultsOnPage = computed(() => this.searchResult()?.resultsOnPage ?? []);

    private readonly defaultSortingOrder = SortingOrder.ASCENDING;

    readonly searchTerm = signal<string>('');
    readonly sortingOrder = signal<SortingOrder>(this.defaultSortingOrder);
    readonly sortedColumn = signal<string>('ID');
    readonly page = signal<number>(1);
    readonly pageSize = signal<number>(10).asReadonly();
    readonly collectionSize = computed(() => {
        const numberOfPages = this.searchResult()?.numberOfPages ?? 1;
        return numberOfPages === 1 ? this.resultsOnPage().length : numberOfPages * this.pageSize();
    });

    constructor() {
        effect(() => {
            untracked(async () => await this.loadData());
        });
    }

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
        this.sortingOrder.set(this.sortedColumn() === sortedColumn ? sortingOrder : this.defaultSortingOrder);
        this.sortedColumn.set(sortedColumn);
        await this.loadData();
    }

    protected async setPage(page: number): Promise<void> {
        this.page.set(page);
        await this.loadData();
    }

    protected search(): void {
        this.debouncedDataLoad();
    }

    protected selectRow(item: T): void {
        this.onRowSelection.emit(item);
    }
}
