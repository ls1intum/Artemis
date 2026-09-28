import { LECTURE_CONTENT_TYPE } from './lecture-content-result.util';

/**
 * The two filter facets in the global-search command palette that ship in this PR.
 * `type` selects entity kinds (mirrors the start-page cards); `course` scopes the search to one or
 * more courses.
 */
export type FacetKind = 'type' | 'course';

/**
 * Display values for the `type` facet, one per start-page entity card. `communication` is a group
 * that expands to channel + post + answer_post on the wire. `course` here means "find course
 * entities" and is intentionally distinct from the `course` scope facet. `lecture_content` is the odd
 * one out: it searches the Iris slide and transcript collections instead of the metadata index, so it
 * expands to no server type and cannot be combined with the others.
 */
export type TypeFacetValue = 'course' | 'exercise' | 'lecture' | 'exam' | 'faq' | 'communication' | typeof LECTURE_CONTENT_TYPE;

/**
 * A single active filter, rendered as one chip. Only the identity is stored; the human-readable label
 * is derived (from the catalog for `type`, from the course list for `course`) so it can never go
 * stale. `negate` marks an exclusion chip.
 */
export interface FilterToken {
    facet: FacetKind;
    value: string;
    negate?: boolean;
}
