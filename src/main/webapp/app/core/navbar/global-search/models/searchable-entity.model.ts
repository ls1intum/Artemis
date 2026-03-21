import { IconDefinition } from '@fortawesome/fontawesome-svg-core';

export interface SearchableEntity {
    id: string;
    title: string;
    description: string;
    icon: IconDefinition;
    type: 'page' | 'feature' | 'course';
    enabled: boolean;
    filterTag?: string;
}
