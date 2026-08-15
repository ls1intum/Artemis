import { FacetKind } from './search-token.model';

/** A `facet:` operator typed into the search input. */
export interface ParsedOperator {
    facet: FacetKind;
    negate: boolean;
    /** The remaining query text after the colon. */
    query: string;
    /** The raw operator prefix as typed, e.g. "course:" or "-type:". */
    prefix: string;
}

const OPERATOR_TO_FACET: Record<string, FacetKind> = {
    course: 'course',
    type: 'type',
};

/**
 * Parses a leading `facet:` operator (optionally `-facet:` to exclude) from the raw input. Returns the
 * facet, whether it is negated, the raw prefix, and the remaining query text, or undefined when the
 * input does not start with a known operator.
 */
export function parseOperator(input: string): ParsedOperator | undefined {
    const match = input.match(/^(-?)([a-zA-Z]+):(.*)$/);
    if (!match) {
        return undefined;
    }
    const facet = OPERATOR_TO_FACET[match[2].toLowerCase()];
    if (!facet) {
        return undefined;
    }
    return { facet, negate: match[1] === '-', query: match[3], prefix: `${match[1]}${match[2]}:` };
}
