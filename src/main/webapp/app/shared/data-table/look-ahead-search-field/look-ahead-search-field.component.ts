import { Component, ElementRef, Input, ViewChild } from '@angular/core';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { BaseEntity } from 'app/shared/model/base-entity';
import { flatten, get } from 'lodash-es';
import { Observable, debounceTime, distinctUntilChanged, map, tap } from 'rxjs';

@Component({
    selector: 'jhi-look-ahead-search-field',
    templateUrl: './look-ahead-search-field.component.html',
    styleUrls: ['./look-ahead-search-field.component.scss'],
})
export class LookAheadSearchFieldComponent {
    @Input() isLoading = false;
    @Input() isSearching = false;
    @Input() searchFailed = false;
    @Input() searchNoResults = false;
    @Input() isTransitioning = false;
    @Input() showPageSizeDropdown = true;
    @Input() showSearchField = true;
    @Input() entityType = 'entity';
    @Input() allEntities: BaseEntity[] = [];
    @Input() entitiesPerPageTranslation: string;
    @Input() showAllEntitiesTranslation: string;
    @Input() searchNoResultsTranslation = 'artemisApp.dataTable.search.noResults';
    @Input() searchPlaceholderTranslation: string;
    @Input() minQueryLengthHintTranslation = 'artemisApp.dataTable.search.minQueryLengthHint';
    @Input() searchFields: string[] = [];
    @Input() searchEnabled = true;
    @Input() searchEntityFilterEnabled = true;
    @Input() searchTextFromEntity: (entity: BaseEntity) => string = entityToString;
    @Input() searchResultFormatter: (entity: BaseEntity) => string = entityToString;
    @Input() onSearchWrapper: (stream: Observable<{ text: string; entities: BaseEntity[] }>) => Observable<BaseEntity[]> = onSearchDefaultWrapper;
    @Input() onAutocompleteSelectWrapper: (entity: BaseEntity, callback: (entity: BaseEntity) => void) => void = onAutocompleteSelectDefaultWrapper;
    @Input() customFilter: (entity: BaseEntity) => boolean = () => true;
    @Input() customFilterKey: any = {};
    entityCriteria: {
        textSearch: string[];
    };
    searchQueryTooShort: boolean;
    readonly minSearchQueryLength = 3;
    entities: BaseEntity[];

    @ViewChild('ngbTypeahead', { static: false }) ngbTypeahead: ElementRef;

    // Icons
    faCircleNotch = faCircleNotch;

    /**
     * Splits the provides search words by comma and updates the autocompletion overlay.
     * Also updates the available entities in the UI.
     *
     * @param text$ stream of text input.
     */
    onSearch = (text$: Observable<string>): Observable<BaseEntity[]> => {
        return this.onSearchWrapper(
            text$.pipe(
                debounceTime(200),
                distinctUntilChanged(),
                tap(() => {
                    this.searchQueryTooShort = false;
                }),
                map((text) => {
                    const searchWords = text.split(',').map((word) => word.trim());
                    // When the entity field is cleared, we translate the resulting empty string to an empty array (otherwise no entities would be found).
                    return { text, searchWords: searchWords.length === 1 && !searchWords[0] ? [] : searchWords };
                }),
                // For autocomplete.
                map(({ text, searchWords }) => {
                    // We only execute the autocomplete for the last keyword in the provided list.
                    const lastSearchWord = searchWords.last();
                    // Don't execute autocomplete for less than two inputted characters.
                    if (!lastSearchWord || lastSearchWord.length < this.minSearchQueryLength) {
                        this.searchQueryTooShort = true;
                        return { text, entities: [] };
                    }
                    return {
                        text,
                        entities: this.entities.filter((entity) => {
                            const fieldValues = this.entityFieldValues(entity, this.searchFields);
                            return fieldValues.some((fieldValue) => this.foundIn(fieldValue)(lastSearchWord));
                        }),
                    };
                }),
            ),
        );
    };
    /**
     * Returns the values that the given entity has in the given fields
     *
     * @param entity Entity whose field values are extracted
     * @param fields Fields to extract from entity (can be paths such as "student.login")
     */
    private entityFieldValues = (entity: BaseEntity, fields: string[]) => {
        return flatten(fields.map((field) => this.collectEntityFieldValues(entity, field))).filter(Boolean) as string[];
    };

    /**
     * Returns the values that the given entity has in the given field.
     * Usually, this will be one value but if the field path contains an array, the rest of the path will be resolved for each array element.
     * Values are merged recursively into a flat list.
     *
     * @param entity Entity whose field values are extracted
     * @param field Field to extract from entity (can be paths such as "student.login" or array path such as "students.login")
     */
    private collectEntityFieldValues = (entity: BaseEntity, field: string): any[] => {
        const separator = '.';
        const [head, ...tail] = field.split(separator);
        if (tail.length > 0) {
            const resolved = get(entity, head);
            if (Array.isArray(resolved)) {
                return flatten(resolved.map((subEntity) => this.collectEntityFieldValues(subEntity, tail.join(separator))));
            }
            return this.collectEntityFieldValues(resolved, tail.join(separator));
        }
        return [get(entity, head, false)];
    };

    /**
     * Performs a case-insensitive search of "word" inside of "text".
     * If "word" consists of multiple segments separated by a space, each one of them must appear in "text".
     * This relaxation has the benefit that searching for "Max Mustermann" will still find "Max Gregor Mustermann".
     * Additionally, the wildcard symbols "*" and "?" are supported.
     *
     * @param text string that is searched for param "word"
     */
    private foundIn = (text: string) => (word: string) => {
        const segments = word.toLowerCase().split(' ');
        return (
            text &&
            word &&
            segments.every((segment) => {
                const regex = segment
                    .replace(/[.+\-^${}()|[\]\\]/g, '\\$&') // escape
                    .replace(/\*/g, '.*') // multiple characters
                    .replace(/\?/g, '.'); // single character
                return new RegExp(regex).test(text.toLowerCase());
            })
        );
    };
    /**
     * Function that is called when the search input emits a blur event.
     * Can be used to clear up search-related info messages.
     */
    onSearchInputBlur() {
        this.searchQueryTooShort = false;
    }

    /**
     * Property that exposes the typeahead buttons (= autocomplete suggestion options) as DOM elements
     */
    get typeaheadButtons() {
        return get(this.ngbTypeahead, 'nativeElement.nextSibling.children', []);
    }

    /**
     * Method is called when user clicks on an autocomplete suggestion. The input method
     * searchTextFromEntity determines how the entity is converted to a searchable string.
     *
     * @param entity Entity that was selected via autocomplete
     */
    onAutocompleteSelect = (entity: BaseEntity) => {
        this.entityCriteria.textSearch[this.entityCriteria.textSearch.length - 1] = this.searchTextFromEntity(entity);
        this.onAutocompleteSelectWrapper(entity, this.filterAfterAutocompleteSelect);
    };

    /**
     * Formats the search input.
     */
    searchInputFormatter = () => {
        return this.entityCriteria.textSearch.join(', ');
    };
    /**
     * Method updates the displayed entities (will be only one entity if the search text is unique per entity).
     */
    filterAfterAutocompleteSelect = () => {
        this.updateEntities();
    };
    /**
     * Updates the UI with all available filter/sort settings.
     * First performs the filtering, then sorts the remaining entities.
     */
    private updateEntities() {}
}

const entityToString = (entity: BaseEntity) => entity.id!.toString();

/**
 * Default on search wrapper that simply strips the search text and passes on the results.
 * This can be customized by supplying your own onSearchWrapper as an Input that e.g. modifies the results.
 * Just copy the default wrapper below into your consumer component (that uses this component) as a blueprint and adapt it.
 *
 * @param stream$ stream of searches of the format {text, entities} where entities are the results
 */
const onSearchDefaultWrapper = (stream$: Observable<{ text: string; entities: BaseEntity[] }>): Observable<BaseEntity[]> => {
    return stream$.pipe(
        map(({ entities }) => {
            return entities;
        }),
    );
};

/**
 * Default on autocomplete select wrapper that simply calls the provided callback (which is this components onAutocompleteSelect).
 * This can be customized by supplying your own onAutocompleteSelectWrapper as an Input that changes or adds behavior.
 * Just copy the default wrapper below into your consumer component (that uses this component) as a blueprint and adapt it.
 *
 * @param entity The selected entity from the autocomplete suggestions
 * @param callback Function that can be called with the selected entity to trigger this component's default behavior for on select
 */
const onAutocompleteSelectDefaultWrapper = (entity: BaseEntity, callback: (entity: BaseEntity) => void): void => {
    callback(entity);
};
