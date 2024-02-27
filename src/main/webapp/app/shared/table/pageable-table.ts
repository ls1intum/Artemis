export interface SearchResult<T> {
    resultsOnPage: T[];
    numberOfPages: number;
}

/**
 * Enumeration specifying sorting order options.
 */
export enum SortingOrder {
    ASCENDING = 'ASCENDING',
    DESCENDING = 'DESCENDING',
}

export interface BasePageableSearch {
    page: number;
    pageSize: number;
    sortingOrder: SortingOrder;
    sortedColumn: string;
}

export interface PageableSearch extends BasePageableSearch {
    searchTerm: string;
}

export interface CompetencyFilter {
    title: string;
    description: string;
    courseTitle: string;
    semester: string;
}

export interface CompetencyPageableSearch extends BasePageableSearch, CompetencyFilter {}
