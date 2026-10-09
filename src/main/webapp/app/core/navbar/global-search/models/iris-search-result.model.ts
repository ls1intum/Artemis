import { EntitySearchSource } from './entity-search-source.model';
import { LectureSearchResult } from './lecture-search-result.model';

export interface IrisSearchResult {
    answer: string | undefined;
    sources: LectureSearchResult[];
    /** Entity sources (course information) the answer drew on; citation numbers continue after `sources`. */
    entitySources?: EntitySearchSource[];
    /**
     * For marker 1..N in `answer`'s citation numbering (in order), which of `sources`/`entitySources`
     * that marker resolves into — see `IrisSearchStatusUpdate.citationSourceTypes`. Undefined for an
     * older Iris that never sent the field.
     */
    citationSourceTypes?: ('lecture' | 'entity')[];
}
