import { CourseInformationSharingConfiguration, Language } from 'app/core/course/shared/entities/course.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';

export interface CourseEnrollmentConfigurationDTO {
    enrollmentEnabled?: boolean;
    enrollmentStartDate?: string;
    enrollmentEndDate?: string;
    enrollmentConfirmationMessage?: string;
    unenrollmentEnabled?: boolean;
    unenrollmentEndDate?: string;
}

export interface CourseComplaintConfigurationDTO {
    maxComplaints?: number;
    maxTeamComplaints?: number;
    maxComplaintTimeDays?: number;
    maxRequestMoreFeedbackTimeDays?: number;
    maxComplaintTextLimit?: number;
    maxComplaintResponseTextLimit?: number;
}

export interface CourseExtendedSettingsDTO {
    description?: string;
    messagingCodeOfConduct?: string;
    courseArchivePath?: string;
}

export interface CourseCreateDTO {
    title?: string;
    shortName?: string;
    semester?: string;
    studentGroupName?: string;
    teachingAssistantGroupName?: string;
    editorGroupName?: string;
    instructorGroupName?: string;
    startDate?: string;
    endDate?: string;
    testCourse?: boolean;
    onlineCourse?: boolean;
    language?: Language;
    defaultProgrammingLanguage?: ProgrammingLanguage;
    color?: string;
    faqEnabled?: boolean;
    learningPathsEnabled?: boolean;
    studentCourseAnalyticsDashboardEnabled?: boolean;
    presentationScore?: number;
    maxPoints?: number;
    accuracyOfScores?: number;
    restrictedAthenaModulesAccess?: boolean;
    timeZone?: string;
    courseInformationSharingConfiguration?: CourseInformationSharingConfiguration;
    enrollmentConfiguration?: CourseEnrollmentConfigurationDTO;
    complaintConfiguration?: CourseComplaintConfigurationDTO;
    extendedSettings?: CourseExtendedSettingsDTO;
}
