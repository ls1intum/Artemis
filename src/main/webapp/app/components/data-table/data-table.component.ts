import { Component, Input, OnInit, OnChanges, SimpleChanges, ContentChild, TemplateRef } from '@angular/core';
import { debounceTime, distinctUntilChanged, map, tap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { ColumnMode, SortType } from '@swimlane/ngx-datatable';
import { SortByPipe } from 'app/components/pipes';
import { compose, filter } from 'lodash/fp';
import { get } from 'lodash';
import { BaseEntity } from 'app/shared';
import * as pluralize from 'pluralize';

enum SortOrder {
    ASC = 'asc',
    DESC = 'desc',
}

enum SortIcon {
    NONE = 'sort',
    ASC = 'sort-up',
    DESC = 'sort-down',
}

const SortOrderIcon = {
    [SortOrder.ASC]: SortIcon.ASC,
    [SortOrder.DESC]: SortIcon.DESC,
};

type SortProp = {
    field: string;
    order: SortOrder;
};

const entityToString = (entity: BaseEntity) => entity.id.toString();

@Component({
    selector: 'jhi-data-table',
    templateUrl: './data-table.component.html',
    styleUrls: ['data-table.component.scss'],
})
export class DataTableComponent implements OnInit, OnChanges {
    @ContentChild(TemplateRef, { read: TemplateRef, static: false })
    templateRef: TemplateRef<any>;

    @Input() isLoading = false;
    @Input() entityType = 'entity';
    @Input() resultName = 'result';
    @Input() allEntities: BaseEntity[] = [];
    @Input() entitiesPerPageTranslation: string;
    @Input() searchPlaceholderTranslation: string;
    @Input() searchFields: string[] = [];
    @Input() searchTextFromEntity: (entity: BaseEntity) => string = entityToString;
    @Input() searchResultFormatter: (entity: BaseEntity) => string = entityToString;
    @Input() customFilterKey: any = {};
    @Input() customFilter: (entity: BaseEntity) => boolean = () => true;

    PAGING_VALUES = [10, 20, 50, 100, 200, 500, 1000, 2000];
    DEFAULT_PAGING_VALUE = 50;

    entities: BaseEntity[];
    entityCriteria: {
        textSearch: string[];
        sortProp: SortProp;
    };

    entitiesPerPage: number;
    isRendering: boolean;

    constructor(private sortByPipe: SortByPipe) {
        this.entities = [];
        this.entityCriteria = {
            textSearch: [],
            sortProp: { field: 'id', order: SortOrder.ASC },
        };

        // explicitly bind these callbacks to their current context
        // so that they can be used from child components
        this.onSort = this.onSort.bind(this);
        this.iconForSortPropField = this.iconForSortPropField.bind(this);
    }

    ngOnInit() {
        this.entitiesPerPage = this.getCachedEntitiesPerPage();
    }

    /**
     * Method is called when Inputs of this component have changed.
     *
     * @param changes List of Inputs that were changed
     */
    ngOnChanges(changes: SimpleChanges) {
        const triggerUpdateList = ['allEntities', 'customFilterKey'];

        if (Object.keys(changes).filter(input => triggerUpdateList.includes(input))) {
            this.updateEntities();
        }
    }

    /**
     * This context will be passed down to templateRef and will be
     * available for binding by the local template let declarations
     */
    get context() {
        return {
            settings: {
                limit: this.entitiesPerPage,
                sortType: SortType.multi,
                columnMode: ColumnMode.force,
                headerHeight: 50,
                footerHeight: 50,
                rowHeight: 'auto',
                rows: this.entities,
                rowClass: '',
                scrollbarH: true,
            },
            controls: {
                iconForSortPropField: this.iconForSortPropField,
                onSort: this.onSort,
            },
        };
    }

    /**
     * Outputs a string stating how many entities exist, e.g. "24 results"
     *
     * @param count How many entities exist
     */
    formatEntityCount(count: number) {
        return pluralize(this.resultName, count, true);
    }

    /**
     * The component is preparing if the data is loading (managed by the parent component)
     * or rendering (managed by this component).
     */
    get isPreparing() {
        return this.isLoading || this.isRendering;
    }

    /**
     * Key that is used for storing this "items per page" setting in local storage
     */
    private get perPageCacheKey() {
        return `${this.entityType}-items-per-page`;
    }

    /**
     * Get "items per page" setting from local storage. If it does not exist, use the default.
     */
    private getCachedEntitiesPerPage = () => {
        const cachedValue = localStorage.getItem(this.perPageCacheKey);
        return cachedValue ? parseInt(cachedValue, 10) : this.DEFAULT_PAGING_VALUE;
    };

    /**
     * Set the number of entities shown per page (and persist it in local storage).
     * Since the rendering takes a bit, show the loading animation until it completes.
     *
     * @param paging Number of entities per page
     */
    setEntitiesPerPage = (paging: number) => {
        this.isRendering = true;
        setTimeout(() => {
            this.entitiesPerPage = paging;
            this.isRendering = false;
        }, 500);
        localStorage.setItem(this.perPageCacheKey, paging.toString());
    };

    /**
     * Updates the UI with all available filter/sort settings.
     * First performs the filtering, then sorts the remaining entities.
     */
    private updateEntities() {
        const filteredEntities = compose(
            filter((entity: BaseEntity) => this.filterEntityByTextSearch(this.entityCriteria.textSearch, entity, this.searchFields)),
            filter(this.customFilter),
        )(this.allEntities);
        this.entities = this.sortByPipe.transform(filteredEntities, this.entityCriteria.sortProp.field, this.entityCriteria.sortProp.order === SortOrder.ASC);
    }

    /**
     * Filter the given entities by the provided search words.
     * Returns entities that match any of the provides search words, if searchWords is empty returns all entities.
     *
     * @param searchWords list of student logins or names
     * @param entity BaseEntity
     * @param searchFields list of paths in entity to search
     */
    private filterEntityByTextSearch = (searchWords: string[], entity: BaseEntity, searchFields: string[]) => {
        // When no search word is inputted, we return all entities.
        if (!searchWords.length) {
            return true;
        }
        // Otherwise we do a fuzzy search on the inputted search words.
        const containsSearchWord = (fieldValue: string) => searchWords.some(this.foundIn(fieldValue));
        return this.entityFieldValues(entity, searchFields).some(containsSearchWord);
    };

    /**
     * Returns the values that the given entity has in the given fields
     *
     * @param entity Entity whose field values are extracted
     * @param fields Fields to extract from entity (can be paths such as "student.login")
     */
    private entityFieldValues = (entity: BaseEntity, fields: string[]) => {
        const getEntityFieldValue = (field: string) => get(entity, field, false);
        return fields.map(getEntityFieldValue).filter(Boolean) as string[];
    };

    /**
     * Performs a case-insensitive search of "word" inside of "text".
     *
     * @param text string that is searched for param "word"
     */
    private foundIn = (text: string) => (word: string) => {
        return word && text.toLowerCase().includes(word.toLowerCase());
    };

    /**
     * Splits the provides search words by comma and updates the autocompletion overlay.
     * Also updates the available entities in the UI.
     *
     * @param text$ stream of text input.
     */
    onSearch = (text$: Observable<string>) => {
        return text$.pipe(
            debounceTime(200),
            distinctUntilChanged(),
            map(text => {
                const searchWords = text.split(',').map(word => word.trim());
                // When the entity field is cleared, we translate the resulting empty string to an empty array (otherwise no entities would be found).
                return searchWords.length === 1 && !searchWords[0] ? [] : searchWords;
            }),
            // For available entities in table.
            tap(searchWords => {
                this.entityCriteria.textSearch = searchWords;
                this.updateEntities();
            }),
            // For autocomplete.
            map((searchWords: string[]) => {
                // We only execute the autocomplete for the last keyword in the provided list.
                const lastSearchWord = searchWords.length ? searchWords[searchWords.length - 1] : null;
                // Don't execute autocomplete for less then two inputted characters.
                if (!lastSearchWord || lastSearchWord.length < 3) {
                    return false;
                }
                return this.entities.filter(entity => {
                    const fieldValues = this.entityFieldValues(entity, this.searchFields);
                    return fieldValues.some(fieldValue => this.foundIn(fieldValue)(lastSearchWord) && fieldValue.toLowerCase() !== lastSearchWord.toLowerCase());
                });
            }),
        );
    };

    /**
     * Method is called when user clicks on an autocomplete suggestion. The input method
     * searchTextFromEntity determines how the entity is converted to a searchable string.
     *
     * @param entity Entity that was selected via autocomplete
     */
    onAutocompleteSelect = (entity: BaseEntity) => {
        this.entityCriteria.textSearch[this.entityCriteria.textSearch.length - 1] = this.searchTextFromEntity(entity);
        this.updateEntities();
    };

    /**
     * Formats the search input.
     */
    searchInputFormatter = () => {
        return this.entityCriteria.textSearch.join(', ');
    };

    /**
     * Sets the selected sort field, then updates the available entities in the UI.
     * Toggles the order direction (asc, desc) when the field has not changed.
     *
     * @param field Entity field
     */
    onSort(field: string) {
        const sameField = this.entityCriteria.sortProp && this.entityCriteria.sortProp.field === field;
        const order = sameField ? this.invertSort(this.entityCriteria.sortProp.order) : SortOrder.ASC;
        this.entityCriteria.sortProp = { field, order };
        this.updateEntities();
    }

    /**
     * Returns the opposite sort order of the given sort order.
     *
     * @param order SortOrder
     */
    private invertSort = (order: SortOrder) => {
        return order === SortOrder.ASC ? SortOrder.DESC : SortOrder.ASC;
    };

    /**
     * Returns the Font Awesome icon name for a column header's sorting icon
     * based on the currently active sortProp field and order.
     *
     * @param field Entity field
     */
    iconForSortPropField(field: string) {
        if (this.entityCriteria.sortProp.field !== field) {
            return SortIcon.NONE;
        }
        return SortOrderIcon[this.entityCriteria.sortProp.order];
    }
}
