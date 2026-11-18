export type FeedbackColor = 'primary' | 'secondary' | 'success' | 'danger' | 'warning' | 'info' | 'light' | 'dark';

export interface FeedbackNode {
    /**
     * Severity of the feedback; the feedback message shows it as its TUM AET UI severity (`success`, `info`, `warning`, `danger`)
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
