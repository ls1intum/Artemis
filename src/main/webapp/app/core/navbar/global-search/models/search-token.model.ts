/**
 * The two filter facets in the global-search command palette that ship in this PR.
 * `type` selects entity kinds (mirrors the start-page cards); `course` scopes the search to one or
 * more courses.
 */
export type FacetKind = 'type' | 'course';

/**
 * Display values for the `type` facet, one per start-page entity card. `communication` is a group
 * that expands to channel + post + answer_post on the wire. `course` here means "find course
 * entities" and is intentionally distinct from the `course` scope facet.
 */
export type TypeFacetValue = 'course' | 'exercise' | 'lecture' | 'exam' | 'faq' | 'communication';

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
