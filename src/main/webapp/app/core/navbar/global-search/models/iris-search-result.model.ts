import { EntitySearchSource } from './entity-search-source.model';
import { LectureSearchResult } from './lecture-search-result.model';

export interface IrisSearchResult {
    answer: string | undefined;
    sources: LectureSearchResult[];
    /** Entity sources (course information) the answer drew on; citation numbers continue after `sources`. */
    entitySources?: EntitySearchSource[];
}
