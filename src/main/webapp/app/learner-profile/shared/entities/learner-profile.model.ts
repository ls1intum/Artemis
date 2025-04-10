export class LearnerProfileDTO {
    public id: number;
    public feedbackPracticalTheoretical: number;
    public feedbackCreativeGuidance: number;
    public feedbackFollowupSummary: number;
    public feedbackBriefDetailed: number;

    constructor(
        id: number = 0,
        feedbackPracticalTheoretical: number = 3,
        feedbackCreativeGuidance: number = 3,
        feedbackFollowupSummary: number = 3,
        feedbackBriefDetailed: number = 3,
    ) {
        this.id = id;
        this.feedbackPracticalTheoretical = feedbackPracticalTheoretical;
        this.feedbackCreativeGuidance = feedbackCreativeGuidance;
        this.feedbackFollowupSummary = feedbackFollowupSummary;
        this.feedbackBriefDetailed = feedbackBriefDetailed;
    }
}
