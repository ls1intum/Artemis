export interface FeedbackNode {
    /**
     * CSS variable specifying the color
     */
    color?: string;
    name: string;
    credits: number | undefined;
    /**
     * Cap e.g. SCA deductions or achievable points from test cases
     * mostly used for {@link FeedbackGroup}
     */
    maxCredits?: number;
}
