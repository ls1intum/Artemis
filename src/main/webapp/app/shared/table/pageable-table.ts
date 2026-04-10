export interface PageableResult<T> {
    content: T[];
    totalElements: number;
    totalPages?: number;
}

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

export interface PageableSearch {
    page: number;
    pageSize: number;
    sortingOrder: SortingOrder;
    sortedColumn: string;
}

export interface SearchTermPageableSearch extends PageableSearch {
    searchTerm: string;
}

export interface CourseCompetencyFilter {
    title: string;
    description: string;
    courseTitle: string;
    semester: string;
}

export interface CompetencyPageableSearch extends PageableSearch, CourseCompetencyFilter {}
