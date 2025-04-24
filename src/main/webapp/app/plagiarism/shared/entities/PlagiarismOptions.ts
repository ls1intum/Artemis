export class PlagiarismOptions {
    /**
     * Ignore comparisons whose similarity is below this threshold (%).
     */
    similarityThreshold: number;

    /**
     * Consider only submissions whose score is greater or equal to this value.
     */
    minimumScore: number;

    /**
     * Consider only submissions whose size is greater or equal to this value.
     */
    minimumSize: number;

    constructor(similarityThreshold: number, minimumScore: number, minimumSize: number) {
        this.similarityThreshold = similarityThreshold;
        this.minimumScore = minimumScore;
        this.minimumSize = minimumSize;
    }

    /**
     * Map the option values to strings so that they can be used as request params.
     */
    toParams() {
        return {
            similarityThreshold: this.similarityThreshold.toString(),
            minimumScore: this.minimumScore.toString(),
            minimumSize: this.minimumSize.toString(),
        };
    }
}
