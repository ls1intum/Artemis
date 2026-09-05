import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faBook, faCalendarCheck, faComments, faCube, faGraduationCap, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { SearchEntityType } from './searchable-entity.model';
import { TypeFacetValue } from './search-token.model';

/** Metadata for one `type` facet value (one start-page card). */
export interface TypeFacetMeta {
    /** i18n key for the chip and start-page card label. */
    labelKey: string;
    /** i18n key for the value-menu description line (reuses the start-page card description). */
    descriptionKey: string;
    /** Icon shown on the chip and the start-page card. */
    icon: IconDefinition;
    /** Server `types` this value expands to. Must match the present-day card `filterTags` exactly. */
    serverTypes: SearchEntityType[];
}

/**
 * Single source of truth mapping each `type` facet value to its label, icon, and server types. The
 * `serverTypes` reproduce today's start-page card `filterTags` exactly, so clicking a card yields
 * identical results to present day.
 */
export const TYPE_FACETS: Record<TypeFacetValue, TypeFacetMeta> = {
    course: { labelKey: 'global.search.entities.coursesTitle', descriptionKey: 'global.search.entities.coursesDescription', icon: faGraduationCap, serverTypes: ['course'] },
    exercise: { labelKey: 'global.search.entities.exercisesTitle', descriptionKey: 'global.search.entities.exercisesDescription', icon: faCube, serverTypes: ['exercise'] },
    lecture: {
        labelKey: 'global.search.entities.lecturesTitle',
        descriptionKey: 'global.search.entities.lecturesDescription',
        icon: faBook,
        serverTypes: ['lecture', 'lecture_unit'],
    },
    communication: {
        labelKey: 'global.search.entities.communicationTitle',
        descriptionKey: 'global.search.entities.communicationDescription',
        icon: faComments,
        serverTypes: ['channel', 'post', 'answer_post'],
    },
    faq: { labelKey: 'global.search.entities.faqsTitle', descriptionKey: 'global.search.entities.faqsDescription', icon: faQuestionCircle, serverTypes: ['faq'] },
    exam: { labelKey: 'global.search.entities.examsTitle', descriptionKey: 'global.search.entities.examsDescription', icon: faCalendarCheck, serverTypes: ['exam'] },
};

/** Start-page card order, unchanged from present day. */
export const TYPE_FACET_ORDER: TypeFacetValue[] = ['course', 'exercise', 'lecture', 'communication', 'faq', 'exam'];

/**
 * Every server entity type, mirroring the server's VALID_TYPES. Used to compute the complement for
 * `type` exclusion, so "exclude X" means "everything except X" (including lecture_unit).
 */
export const ALL_SEARCHABLE_TYPES: SearchEntityType[] = ['exercise', 'lecture', 'lecture_unit', 'exam', 'faq', 'channel', 'course', 'post', 'answer_post'];
