import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { FacetKind } from './search-token.model';

/**
 * A single filter chip, fully resolved for display. The search-input renders these and reports
 * removals/edits, but does not know about tokens or the facet catalog.
 */
export interface FilterChipView {
    /** Stable identity for @for tracking (facet + value + negate); unique even when two courses share a title. */
    key: string;
    /** Value text (entity kind or course title). */
    label: string;
    /** Muted facet prefix (translated "type" / "course"). */
    facetLabel: string;
    /** Coloured leading icon. */
    icon: IconDefinition;
    /** Facet family, drives the accent colour (border, icon, divider). */
    family: FacetKind;
    /** Exclusion chip: red accent, ban icon, strikethrough value. */
    negate: boolean;
    /** Keyboard-selected chip: uses the subtle hover treatment. */
    selected: boolean;
}

/**
 * What selecting a menu row does: the guided picker injects an operator prefix into the input; the
 * value menu contributes a value that the modal turns into a token for the active operator's facet.
 */
export type FilterMenuAction = { kind: 'operator'; prefix: string } | { kind: 'value'; value: string };

/** One selectable row in the filter menu (guided picker or facet value list). */
export interface FilterMenuOption {
    /** Stable identity for tracking. */
    id: string;
    label: string;
    icon: IconDefinition;
    /** Operator syntax shown as a mono pill on the right (guided-picker rows only), e.g. "type:". */
    hint?: string;
    /** What choosing this row does. */
    action: FilterMenuAction;
}
