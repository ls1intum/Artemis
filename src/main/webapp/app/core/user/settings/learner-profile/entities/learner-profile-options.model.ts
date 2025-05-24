export interface LearnerProfileOption {
    level: number;
    translationKey: string;
}

export const ALTERNATIVE_STANDARD_OPTIONS: LearnerProfileOption[] = [
    { level: 1, translationKey: 'artemisApp.learnerProfile.feedbackLearnerProfile.alternativeStandard.level.alternative' },
    { level: 2, translationKey: 'artemisApp.learnerProfile.feedbackLearnerProfile.alternativeStandard.level.balanced' },
    { level: 3, translationKey: 'artemisApp.learnerProfile.feedbackLearnerProfile.alternativeStandard.level.standard' },
];

export const FOLLOWUP_SUMMARY_OPTIONS: LearnerProfileOption[] = [
    { level: 1, translationKey: 'artemisApp.learnerProfile.feedbackLearnerProfile.followupSummary.level.followup' },
    { level: 2, translationKey: 'artemisApp.learnerProfile.feedbackLearnerProfile.followupSummary.level.balanced' },
    { level: 3, translationKey: 'artemisApp.learnerProfile.feedbackLearnerProfile.followupSummary.level.summary' },
];

export const BRIEF_DETAILED_OPTIONS: LearnerProfileOption[] = [
    { level: 1, translationKey: 'artemisApp.learnerProfile.feedbackLearnerProfile.briefDetailed.level.brief' },
    { level: 2, translationKey: 'artemisApp.learnerProfile.feedbackLearnerProfile.briefDetailed.level.balanced' },
    { level: 3, translationKey: 'artemisApp.learnerProfile.feedbackLearnerProfile.briefDetailed.level.detailed' },
];
