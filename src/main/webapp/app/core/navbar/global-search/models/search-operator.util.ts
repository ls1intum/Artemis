import { FacetKind } from './search-token.model';

/** A `facet:` operator typed into the search input, together with the search text that precedes it. */
export interface ParsedOperator {
    facet: FacetKind;
    negate: boolean;
    /** The value text after the colon. Runs to the end of the input, so course titles keep their spaces. */
    query: string;
    /** The raw operator prefix as typed, e.g. "course:" or "-type:". */
    prefix: string;
    /** Index in the raw input where the operator starts, so callers can strip it back off. */
    start: number;
    /** The search text before the operator, trimmed. */
    text: string;
}

const OPERATOR_TO_FACET: Record<string, FacetKind> = {
    course: 'course',
    type: 'type',
};

/** Matches a `facet:` operator at the start of the input or after whitespace, optionally negated with `-`. */
const OPERATOR_PATTERN = /(^|\s)(-?)([a-zA-Z]+):/g;

/**
 * Parses the trailing `facet:` operator out of the raw input: the last known `[-]facet:` occurrence and
 * everything after it, with the text before it returned as the search term.
 * <p>
 * The operator is a token inside the query rather than a mode over it, which is what lets a filter be
 * composed without evicting what the user is searching for. Letting the value run to the end of the input
 * (rather than to the next space) is what keeps course titles with spaces usable. Returns undefined when
 * the input carries no known operator, in which case all of it is search text.
 */
export function parseOperator(input: string): ParsedOperator | undefined {
    let last: RegExpExecArray | undefined;
    for (const candidate of input.matchAll(OPERATOR_PATTERN)) {
        if (OPERATOR_TO_FACET[candidate[3].toLowerCase()]) {
            last = candidate;
        }
    }
    if (!last || last.index === undefined) {
        return undefined;
    }
    const start = last.index + last[1].length;
    const prefix = `${last[2]}${last[3]}:`;
    return {
        facet: OPERATOR_TO_FACET[last[3].toLowerCase()],
        negate: last[2] === '-',
        query: input.slice(start + prefix.length),
        prefix,
        start,
        text: input.slice(0, last.index).trim(),
    };
}

/** The text the server should search for: everything before a trailing operator, or the whole input. */
export function searchTextOf(input: string): string {
    return (parseOperator(input)?.text ?? input).trim();
}

/** Removes a trailing operator from the input, leaving the search text the user typed before it. */
export function stripOperator(input: string): string {
    const operator = parseOperator(input);
    return operator ? input.slice(0, operator.start).replace(/\s+$/, '') : input;
}

/** Appends an operator prefix to the input, keeping whatever the user was already searching for. */
export function appendOperator(input: string, prefix: string): string {
    const head = input.replace(/\s+$/, '');
    return head ? `${head} ${prefix}` : prefix;
}
