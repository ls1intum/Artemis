export type FeedbackColor = 'primary' | 'secondary' | 'success' | 'danger' | 'warning' | 'info' | 'light' | 'dark';

export interface FeedbackNode {
    /**
     * One of the possible colors for bootstrap alerts
     */
    color?: FeedbackColor;
    name: string;
    credits: number | undefined;
    /**
     * Cap e.g. SCA deductions or achievable points from test cases
     * mostly used for {@link FeedbackGroup}
     */
    maxCredits?: number;
}
