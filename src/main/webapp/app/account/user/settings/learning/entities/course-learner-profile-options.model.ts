export interface CourseLearnerProfileOption {
    level: number;
    translationKey: string;
}

export const COURSE_LEARNER_PROFILE_OPTIONS: CourseLearnerProfileOption[] = [
    { level: 1, translationKey: 'artemisApp.learnerProfile.courseLearnerProfile.level.veryLow' },
    { level: 2, translationKey: 'artemisApp.learnerProfile.courseLearnerProfile.level.low' },
    { level: 3, translationKey: 'artemisApp.learnerProfile.courseLearnerProfile.level.medium' },
    { level: 4, translationKey: 'artemisApp.learnerProfile.courseLearnerProfile.level.high' },
    { level: 5, translationKey: 'artemisApp.learnerProfile.courseLearnerProfile.level.veryHigh' },
];
