import { EntitySearchSource } from 'app/core/navbar/global-search/models/entity-search-source.model';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';

/** The sources an Iris global search answer cites, and how its `[n]` markers number them. */
export interface CitedSources {
    sources: LectureSearchResult[];
    entitySources: EntitySearchSource[];
    /** For marker 1..N, which of the two arrays it resolves into; undefined for an older Iris that never sent it. */
    citationSourceTypes?: ('lecture' | 'entity')[];
}

/** Where a citation marker points: the array it resolves into and its position in that array. */
export interface ResolvedCitation {
    type: 'lecture' | 'entity';
    index: number;
}

/**
 * The citation marker number (matching the answer's `[n]` numbers) for each entry of `sources`
 * (type 'lecture') or `entitySources` (type 'entity'). `citationSourceTypes` lists every marker 1..N
 * in order with which array it resolves into; the i-th entry of a given type in that list is exactly
 * the i-th entry of that type's own array, since both arrays are built server-side to preserve their
 * own subsequence of that same reading order. Falls back to the old fixed block-order numbering (every
 * lecture marker, then every entity marker) when the terminal update carries no `citationSourceTypes`
 * at all (an older Iris), the wire format's only implicit contract before this field existed.
 */
export function markerNumbersForType(cited: CitedSources, type: 'lecture' | 'entity'): number[] {
    const types = cited.citationSourceTypes;
    if (!types || types.length === 0) {
        const count = type === 'lecture' ? cited.sources.length : cited.entitySources.length;
        const offset = type === 'lecture' ? 0 : cited.sources.length;
        return Array.from({ length: count }, (_, i) => offset + i + 1);
    }
    const numbers: number[] = [];
    types.forEach((t, i) => {
        if (t === type) {
            numbers.push(i + 1);
        }
    });
    return numbers;
}

/**
 * Resolves a citation marker number to which array it belongs to and its position within that
 * array's own ordering, the inverse of {@link markerNumbersForType}: counting occurrences of the
 * marker's own type up to its position in `citationSourceTypes` recovers the right index, since
 * that is exactly how the index was assigned in the first place. Falls back to the old fixed
 * block-order assumption when the terminal update carries no `citationSourceTypes` (an older Iris).
 */
export function resolveCitation(cited: CitedSources, sourceNumber: number): ResolvedCitation | undefined {
    const types = cited.citationSourceTypes;
    if (!types || types.length === 0) {
        return sourceNumber <= cited.sources.length ? { type: 'lecture', index: sourceNumber - 1 } : { type: 'entity', index: sourceNumber - cited.sources.length - 1 };
    }
    const type = types[sourceNumber - 1];
    if (!type) {
        return undefined;
    }
    let index = 0;
    for (let i = 0; i < sourceNumber - 1; i++) {
        if (types[i] === type) {
            index++;
        }
    }
    return { type, index };
}

/** The lecture source a citation marker points at, if it points at one. */
export function citedLectureSource(cited: CitedSources, sourceNumber: number): LectureSearchResult | undefined {
    const resolved = resolveCitation(cited, sourceNumber);
    return resolved?.type === 'lecture' ? cited.sources[resolved.index] : undefined;
}

/** The entity source a citation marker points at, if it points at one. */
export function citedEntitySource(cited: CitedSources, sourceNumber: number): EntitySearchSource | undefined {
    const resolved = resolveCitation(cited, sourceNumber);
    return resolved?.type === 'entity' ? cited.entitySources[resolved.index] : undefined;
}
