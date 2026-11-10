import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { BaseEntity } from 'app/foundation/model/base-entity';
import { PagingService } from 'app/exercise/services/paging.service';
import { SearchResult, SortingOrder } from 'app/foundation/pagination/pageable-table';
import { lastValueFrom } from 'rxjs';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { BaseApiHttpService } from 'app/foundation/service/base-api-http.service';
import { NgbTypeaheadModule } from '@ng-bootstrap/ng-bootstrap';
import {
    TumAetUiButtonDirective,
    TumAetUiInputDirective,
    TumAetUiPaginatorComponent,
    TumAetUiTableDirective,
    TumAetUiTableLayoutComponent,
    TumAetUiTableSortEvent,
    TumAetUiTableSortableColumnComponent,
} from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

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
    imports: [
        TumAetUiPaginatorComponent,
        TumAetUiTableDirective,
        TumAetUiTableLayoutComponent,
        TumAetUiTableSortableColumnComponent,
        TumAetUiButtonDirective,
        TumAetUiInputDirective,
        ArtemisTranslatePipe,
        TranslateDirective,
        FormsModule,
        NgbTypeaheadModule,
    ],
    templateUrl: './import-table.component.html',
})
export class ImportTableComponent<T extends BaseEntity> {
    private readonly alertService = inject(AlertService);
    private readonly pagingService = inject(PagingService);

    protected readonly SortingOrder = SortingOrder;

    columns = input.required<Column<T>[]>();
    readonly columnBaseTranslationKey = input.required<string>();
    disabledIds = input<number[]>([]);

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
            void untracked(async () => await this.loadData());
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
            const searchState = {
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
        return cloneWith(searchResults, { resultsOnPage: searchResults.resultsOnPage?.filter((entity) => !this.disabledIds().includes(entity.id!)) });
    }

    protected async onSortChange(event: TumAetUiTableSortEvent): Promise<void> {
        // Derive the order from the component state rather than the event: the table input lags behind until the next change detection.
        const toggled = this.sortingOrder() === SortingOrder.ASCENDING ? SortingOrder.DESCENDING : SortingOrder.ASCENDING;
        this.sortingOrder.set(this.sortedColumn() === event.field ? toggled : this.DEFAULT_SORTING_ORDER);
        this.sortedColumn.set(event.field);
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
