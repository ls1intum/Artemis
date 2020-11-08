/**
 * A match is a sequence of identical tokens from two submissions.
 */
interface JPlagMatch {
    /**
     * Index of the first token of submission A that is part of this match.
     */
    startA: number;

    /**
     * Index of the first token of submission B that is part of this match.
     */
    startB: number;

    /**
     * Length of the sequence of identical tokens.
     */
    length: number;
}
