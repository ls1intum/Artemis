export interface LearnerProfileData {
    id?: number;
    isBriefFeedback?: boolean;
    isFormalFeedback?: boolean;
    hasSetupFeedbackPreferences?: boolean;
}

export class LearnerProfileDTO {
    public id: number;
    public isBriefFeedback: boolean;
    public isFormalFeedback: boolean;
    public hasSetupFeedbackPreferences?: boolean;

    constructor(data: LearnerProfileData) {
        if (!data) {
            throw new Error('LearnerProfileDTO: data parameter cannot be null or undefined');
        }
        this.id = data.id ?? 0;
        this.isBriefFeedback = data.isBriefFeedback ?? false;
        this.isFormalFeedback = data.isFormalFeedback ?? false;
        this.hasSetupFeedbackPreferences = data.hasSetupFeedbackPreferences;
    }
}
