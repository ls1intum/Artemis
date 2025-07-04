export interface LearnerProfileData {
    id?: number;
    feedbackAlternativeStandard?: number;
    feedbackFollowupSummary?: number;
    feedbackBriefDetailed?: number;
}

export class LearnerProfileDTO {
    /**
     * Minimum value allowed for profile fields.
     * Must be the same as in the server in LearnerProfile.java
     */
    public static readonly MIN_VALUE = 1;
    /**
     * Default value for profile fields.
     * Must be the same as in the server in LearnerProfile.java
     */
    public static readonly DEFAULT_VALUE = 2;
    /**
     * Maximum value allowed for profile fields.
     * Must be the same as in the server in LearnerProfile.java
     */
    public static readonly MAX_VALUE = 3;

    public id: number;
    public feedbackAlternativeStandard: number;
    public feedbackFollowupSummary: number;
    public feedbackBriefDetailed: number;

    constructor(data: LearnerProfileData) {
        if (!data) {
            throw new Error('LearnerProfileDTO: data parameter cannot be null or undefined');
        }

        // Initialize with default values
        this.id = data.id ?? 0;
        this.feedbackAlternativeStandard = this.validateAndGetValue(data.feedbackAlternativeStandard, 'feedbackAlternativeStandard');
        this.feedbackFollowupSummary = this.validateAndGetValue(data.feedbackFollowupSummary, 'feedbackFollowupSummary');
        this.feedbackBriefDetailed = this.validateAndGetValue(data.feedbackBriefDetailed, 'feedbackBriefDetailed');
    }

    private validateAndGetValue(value: number | undefined, fieldName: string): number {
        if (value === undefined || value === null) {
            return LearnerProfileDTO.DEFAULT_VALUE;
        }

        if (!this.isValueInRange(value)) {
            return LearnerProfileDTO.DEFAULT_VALUE;
        }

        return value;
    }

    public isValid(): boolean {
        return this.isValueInRange(this.feedbackAlternativeStandard) && this.isValueInRange(this.feedbackFollowupSummary) && this.isValueInRange(this.feedbackBriefDetailed);
    }

    private isValueInRange(value: number): boolean {
        return value >= LearnerProfileDTO.MIN_VALUE && value <= LearnerProfileDTO.MAX_VALUE;
    }
}
