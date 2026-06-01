import { IconDefinition } from '@fortawesome/fontawesome-svg-core';

export type SearchEntityType = 'exercise' | 'lecture' | 'lecture_unit' | 'exam' | 'faq' | 'channel' | 'course' | 'post' | 'answer_post';

export interface SearchableEntity {
    id: string;
    title: string;
    description: string;
    icon: IconDefinition;
    type: 'filter' | 'feature' | 'course';
    enabled: boolean;
    filterTags?: SearchEntityType[];
}
