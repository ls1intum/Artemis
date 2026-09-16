import { FilterToken } from './search-token.model';
import { TYPE_FACETS } from './facet-catalog';
import { SearchEntityType } from './searchable-entity.model';

/**
 * Adds a token, or toggles it off if an identical one (same facet, value, and negate) is already
 * present. Returns a new array; never mutates the input.
 */
export function addOrToggleToken(tokens: FilterToken[], token: FilterToken): FilterToken[] {
    // Each facet is single-mode: inclusions and exclusions are mutually exclusive. "Only these types" and
    // "everything except these" (and the same for course scope) cannot coexist without leaving dead chips,
    // so adding a token in one mode first drops any tokens of the same facet in the other mode.
    const base = tokens.filter((existing) => !(existing.facet === token.facet && !!existing.negate !== !!token.negate));
    // After that drop, any surviving same-facet token has the same negate, so a matching facet+value is an
    // exact duplicate: toggle it off. Otherwise append.
    const index = base.findIndex((existing) => existing.facet === token.facet && existing.value === token.value);
    return index >= 0 ? base.filter((_, i) => i !== index) : [...base, token];
}

/** Removes the token at the given index. Returns a new array; never mutates the input. */
export function removeTokenAt(tokens: FilterToken[], index: number): FilterToken[] {
    if (index < 0 || index >= tokens.length) {
        return tokens;
    }
    return tokens.filter((_, i) => i !== index);
}

/**
 * Expands the positive `type` tokens into the server `types` query value.
 * <p>
 * Exclusions are deliberately not folded in here as a complement. "Everything except exercises" and "only exams"
 * produce the identical type list, so the server cannot tell them apart, and its exam-exercise expansion fires for
 * both. {@link excludedTypeTokens} carries the exclusions under their own name instead, which keeps the two distinct.
 *
 * @param tokens the active filter tokens
 * @return the comma-separated server types, or undefined when no type is positively selected
 */
export function expandTypeTokens(tokens: FilterToken[]): string | undefined {
    const positives = tokens.filter((token) => token.facet === 'type' && !token.negate);
    if (positives.length === 0) {
        return undefined;
    }
    const serverTypes = unique(positives.flatMap((token) => serverTypesFor(token.value)));
    return serverTypes.length > 0 ? serverTypes.join(',') : undefined;
}

/**
 * The server types to hide, from the negated `type` tokens.
 *
 * @param tokens the active filter tokens
 * @return the comma-separated server types to hide, or undefined when nothing is excluded
 */
export function excludedTypeTokens(tokens: FilterToken[]): string | undefined {
    const negatives = tokens.filter((token) => token.facet === 'type' && token.negate);
    if (negatives.length === 0) {
        return undefined;
    }
    const serverTypes = unique(negatives.flatMap((token) => serverTypesFor(token.value)));
    return serverTypes.length > 0 ? serverTypes.join(',') : undefined;
}

/** The positive `course` scope selections as course ids. */
export function selectedCourseIds(tokens: FilterToken[]): number[] {
    return courseIdsWithNegate(tokens, false);
}

/** The negated `course` selections (courses to exclude) as course ids. */
export function excludedCourseIds(tokens: FilterToken[]): number[] {
    return courseIdsWithNegate(tokens, true);
}

function courseIdsWithNegate(tokens: FilterToken[], negate: boolean): number[] {
    return tokens
        .filter((token) => token.facet === 'course' && !!token.negate === negate)
        .map((token) => Number(token.value))
        .filter((id) => Number.isFinite(id));
}

function serverTypesFor(typeValue: string): SearchEntityType[] {
    return TYPE_FACETS[typeValue as keyof typeof TYPE_FACETS]?.serverTypes ?? [];
}

function unique(values: SearchEntityType[]): SearchEntityType[] {
    return [...new Set(values)];
}
