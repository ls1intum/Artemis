import { HttpResponse } from '@angular/common/http';

export interface PageableResult<T> {
    content: T[];
    totalElements: number;
    totalPages?: number;
}

/**
 * Maps a paged list response to a {@link PageableResult}, reading the total from the `X-Total-Count` header.
 */
export const toPageableResult = <T>(res: HttpResponse<T[]>): PageableResult<T> => ({
    content: res.body ?? [],
    totalElements: Number(res.headers.get('X-Total-Count') ?? 0),
});

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

export interface ParticipationScoreSearch extends SearchTermPageableSearch {
    filterProp?: string;
    scoreRangeLower?: number;
    scoreRangeUpper?: number;
}

export interface ParticipationSearch extends SearchTermPageableSearch {
    filterProp?: string;
}
