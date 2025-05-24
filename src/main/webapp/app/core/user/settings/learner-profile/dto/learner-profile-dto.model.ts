export class LearnerProfileDTO {
    /**
     * Minimum value allowed for profile fields representing values on a Likert scale.
     * Must be the same as in the server in LearnerProfile.java
     */
    public static readonly MIN_VALUE = 1;
    /**
     * Maximum value allowed for profile fields representing values on a Likert scale.
     * Must be the same as in the server in LearnerProfile.java
     */
    public static readonly MAX_VALUE = 5;

    public id: number;
    public feedbackAlternativeStandard: number;
    public feedbackFollowupSummary: number;
    public feedbackBriefDetailed: number;

    constructor(data: any) {
        this.id = data.id;
        this.feedbackAlternativeStandard = data.feedbackAlternativeStandard;
        this.feedbackFollowupSummary = data.feedbackFollowupSummary;
        this.feedbackBriefDetailed = data.feedbackBriefDetailed;
    }

    public isValid(): boolean {
        return this.isValueInRange(this.feedbackAlternativeStandard) && this.isValueInRange(this.feedbackFollowupSummary) && this.isValueInRange(this.feedbackBriefDetailed);
    }

    private isValueInRange(value: number): boolean {
        return value >= LearnerProfileDTO.MIN_VALUE && value <= LearnerProfileDTO.MAX_VALUE;
    }
}
