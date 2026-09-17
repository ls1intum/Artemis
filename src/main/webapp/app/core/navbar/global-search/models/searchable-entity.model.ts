import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { LECTURE_CONTENT_TYPE } from 'app/core/navbar/global-search/models/lecture-content-result.util';

export type SearchEntityType = 'exercise' | 'lecture' | 'lecture_unit' | 'exam' | 'faq' | 'channel' | 'course' | 'post' | 'answer_post';

/**
 * A filter the palette can apply: a searchable entity type, or the slides and videos filter. The latter routes to Iris
 * content search instead of the metadata search, so it is never sent as a server entity type.
 */
export type SearchFilterTag = SearchEntityType | typeof LECTURE_CONTENT_TYPE;

export interface SearchableEntity {
    id: string;
    title: string;
    description: string;
    icon: IconDefinition;
    type: 'filter' | 'feature' | 'course';
    enabled: boolean;
    filterTags?: SearchFilterTag[];
}
