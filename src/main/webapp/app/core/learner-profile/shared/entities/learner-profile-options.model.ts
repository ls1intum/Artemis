export enum CourseLearnerProfileLevel {
    VERY_LOW = 1,
    LOW = 2,
    MEDIUM = 3,
    HIGH = 4,
    VERY_HIGH = 5,
}

export interface CourseLearnerProfileOption {
    level: CourseLearnerProfileLevel;
    translationKey: string;
}

export const COURSE_LEARNER_PROFILE_OPTIONS: CourseLearnerProfileOption[] = [
    { level: CourseLearnerProfileLevel.VERY_LOW, translationKey: 'artemisApp.learnerProfile.courseLearnerProfile.level.veryLow' },
    { level: CourseLearnerProfileLevel.LOW, translationKey: 'artemisApp.learnerProfile.courseLearnerProfile.level.low' },
    { level: CourseLearnerProfileLevel.MEDIUM, translationKey: 'artemisApp.learnerProfile.courseLearnerProfile.level.medium' },
    { level: CourseLearnerProfileLevel.HIGH, translationKey: 'artemisApp.learnerProfile.courseLearnerProfile.level.high' },
    { level: CourseLearnerProfileLevel.VERY_HIGH, translationKey: 'artemisApp.learnerProfile.courseLearnerProfile.level.veryHigh' },
];
