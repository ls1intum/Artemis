/**
 * A `PlagiarismMatch` is a sequence of identical elements of both submissions.
 */
export class PlagiarismMatch {
    /**
     * Index of the first element of submission A that is part of this match.
     */
    startA: number;

    /**
     * Index of the first element of submission B that is part of this match.
     */
    startB: number;

    /**
     * Length of the sequence of identical elements, beginning at startA and startB, respectively.
     */
    length: number;
}
