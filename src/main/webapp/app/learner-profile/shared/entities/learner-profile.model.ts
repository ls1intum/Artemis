export class CourseLearnerProfileDTO {
    public id: number;
    public courseId: number;
    public aimForGradeOrBonus: number;
    public timeInvestment: number;
    public repetitionIntensity: number;
}

export class LearnerProfileDTO {
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
}
