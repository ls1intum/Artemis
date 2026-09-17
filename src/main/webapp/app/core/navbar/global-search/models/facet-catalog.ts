import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faBook, faCalendarCheck, faComments, faCube, faGraduationCap, faPhotoFilm, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { SearchEntityType } from './searchable-entity.model';
import { TypeFacetValue } from './search-token.model';
import { LECTURE_CONTENT_TYPE } from './lecture-content-result.util';

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
    /**
     * Whether this value searches the Iris slide and transcript collections instead of the metadata index.
     * Such a value carries no server type and is never combined with one: the two searches answer from
     * different collections, and one result list cannot mix them.
     */
    contentSearch?: true;
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
    [LECTURE_CONTENT_TYPE]: {
        labelKey: 'global.search.entities.slidesAndVideosTitle',
        descriptionKey: 'global.search.entities.slidesAndVideosDescription',
        icon: faPhotoFilm,
        serverTypes: [],
        contentSearch: true,
    },
    faq: { labelKey: 'global.search.entities.faqsTitle', descriptionKey: 'global.search.entities.faqsDescription', icon: faQuestionCircle, serverTypes: ['faq'] },
    exam: { labelKey: 'global.search.entities.examsTitle', descriptionKey: 'global.search.entities.examsDescription', icon: faCalendarCheck, serverTypes: ['exam'] },
};

/** Start-page card order, unchanged from present day. */
export const TYPE_FACET_ORDER: TypeFacetValue[] = ['course', 'exercise', 'lecture', LECTURE_CONTENT_TYPE, 'communication', 'faq', 'exam'];

/** True when the value searches lecture content (slides and video transcripts) rather than entity metadata. */
export function isContentSearchValue(value: string): boolean {
    return TYPE_FACETS[value as TypeFacetValue]?.contentSearch === true;
}
