export interface LearnerProfileData {
    id?: number;
    feedbackDetail?: number;
    feedbackFormality?: number;
    hasSetupFeedbackPreferences?: boolean;
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
    public feedbackDetail: number;
    public feedbackFormality: number;
    public hasSetupFeedbackPreferences?: boolean;

    constructor(data: LearnerProfileData) {
        if (!data) {
            throw new Error('LearnerProfileDTO: data parameter cannot be null or undefined');
        }
        this.id = data.id ?? 0;
        this.feedbackDetail = this.validateAndGetValue(data.feedbackDetail, 'feedbackDetail');
        this.feedbackFormality = this.validateAndGetValue(data.feedbackFormality, 'feedbackFormality');
        this.hasSetupFeedbackPreferences = data.hasSetupFeedbackPreferences;
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
        return this.isValueInRange(this.feedbackDetail) && this.isValueInRange(this.feedbackFormality);
    }

    private isValueInRange(value: number): boolean {
        return value >= LearnerProfileDTO.MIN_VALUE && value <= LearnerProfileDTO.MAX_VALUE;
    }
}
