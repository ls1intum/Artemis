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
 * What selecting a menu row does: the guided picker appends an operator prefix to the input; the value
 * menu contributes a value that becomes a token for the active operator's facet; `excludeStep` moves the
 * picker into its exclude level without touching the input; `literal` abandons the operator reading and
 * searches for the raw text instead; `clearValue` drops an unmatched value and shows the facet's full list,
 * which is the recovery for a mistyped value rather than for a search that merely contains a colon.
 */
export type FilterMenuAction =
    { kind: 'operator'; prefix: string } | { kind: 'value'; value: string } | { kind: 'excludeStep' } | { kind: 'literal'; text: string } | { kind: 'clearValue' };

/** One selectable row in the filter menu (guided picker or facet value list). */
export interface FilterMenuOption {
    /** Stable identity for tracking. */
    id: string;
    label: string;
    /** Secondary line under the label, so the rows read at the same size as the result / entity rows. */
    description?: string;
    icon: IconDefinition;
    /** Operator syntax shown as a mono pill on the right (guided-picker rows only), e.g. "type:". */
    hint?: string;
    /** Raw text a `literal` row would search for, rendered in mono after the label. */
    literal?: string;
    /** What choosing this row does. */
    action: FilterMenuAction;
}

/**
 * DOM id of the filter-menu listbox. Shared so the search input's `aria-controls` / `aria-activedescendant`
 * can reference the listbox even though it renders in a different component (the results pane).
 */
export const FILTER_MENU_LISTBOX_ID = 'global-search-filter-menu';

/** DOM id for a single menu option, referenced by the input's `aria-activedescendant`. */
export function filterOptionDomId(optionId: string): string {
    return `gs-filter-option-${optionId}`;
}
