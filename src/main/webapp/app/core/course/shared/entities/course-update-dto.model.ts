import { convertDateFromClient } from 'app/shared/util/date.utils';
import { Course, CourseInformationSharingConfiguration, Language } from './course.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';

/**
 * DTO for creating a new course.
 * Mirrors the server-side CourseCreateDTO to send only the necessary fields.
 * Note: No 'id' field since this is for creation.
 */
export interface CourseCreateDTO {
    // Basic info
    title: string;
    shortName: string;
    description?: string;
    semester?: string;

    // Group names
    studentGroupName?: string;
    teachingAssistantGroupName?: string;
    editorGroupName?: string;
    instructorGroupName?: string;

    // Dates (as ISO strings for server)
    startDate?: string;
    endDate?: string;
    enrollmentStartDate?: string;
    enrollmentEndDate?: string;
    unenrollmentEndDate?: string;

    // Configuration flags
    testCourse: boolean;
    onlineCourse?: boolean;
    language?: Language;
    defaultProgrammingLanguage?: ProgrammingLanguage;

    // Complaint settings
    maxComplaints?: number;
    maxTeamComplaints?: number;
    maxComplaintTimeDays: number;
    maxRequestMoreFeedbackTimeDays: number;
    maxComplaintTextLimit: number;
    maxComplaintResponseTextLimit: number;

    // UI settings
    color?: string;
    enrollmentEnabled?: boolean;
    enrollmentConfirmationMessage?: string;
    unenrollmentEnabled: boolean;

    // Course features
    faqEnabled: boolean;
    learningPathsEnabled: boolean;
    studentCourseAnalyticsDashboardEnabled: boolean;
    presentationScore?: number;
    maxPoints?: number;
    accuracyOfScores?: number;
    restrictedAthenaModulesAccess: boolean;
    timeZone?: string;
    courseInformationSharingConfiguration?: CourseInformationSharingConfiguration;
}

/**
 * Converts a Course entity to a CourseCreateDTO for sending to the server.
 *
 * @param course the course entity to convert
 * @returns a CourseCreateDTO with only the fields needed for creation
 */
export function toCourseCreateDTO(course: Course): CourseCreateDTO {
    return {
        // Basic info
        title: course.title!,
        shortName: course.shortName!,
        description: course.description,
        semester: course.semester,

        // Group names
        studentGroupName: course.studentGroupName,
        teachingAssistantGroupName: course.teachingAssistantGroupName,
        editorGroupName: course.editorGroupName,
        instructorGroupName: course.instructorGroupName,

        // Dates (converted to ISO strings)
        startDate: convertDateFromClient(course.startDate),
        endDate: convertDateFromClient(course.endDate),
        enrollmentStartDate: convertDateFromClient(course.enrollmentStartDate),
        enrollmentEndDate: convertDateFromClient(course.enrollmentEndDate),
        unenrollmentEndDate: convertDateFromClient(course.unenrollmentEndDate),

        // Configuration flags
        testCourse: course.testCourse ?? false,
        onlineCourse: course.onlineCourse,
        language: course.language,
        defaultProgrammingLanguage: course.defaultProgrammingLanguage,

        // Complaint settings
        maxComplaints: course.maxComplaints,
        maxTeamComplaints: course.maxTeamComplaints,
        maxComplaintTimeDays: course.maxComplaintTimeDays ?? 7,
        maxRequestMoreFeedbackTimeDays: course.maxRequestMoreFeedbackTimeDays ?? 7,
        maxComplaintTextLimit: course.maxComplaintTextLimit ?? 2000,
        maxComplaintResponseTextLimit: course.maxComplaintResponseTextLimit ?? 2000,

        // UI settings
        color: course.color,
        enrollmentEnabled: course.enrollmentEnabled,
        enrollmentConfirmationMessage: course.enrollmentConfirmationMessage,
        unenrollmentEnabled: course.unenrollmentEnabled ?? false,

        // Course features
        faqEnabled: course.faqEnabled ?? false,
        learningPathsEnabled: course.learningPathsEnabled ?? false,
        studentCourseAnalyticsDashboardEnabled: course.studentCourseAnalyticsDashboardEnabled ?? false,
        presentationScore: course.presentationScore,
        maxPoints: course.maxPoints,
        accuracyOfScores: course.accuracyOfScores,
        restrictedAthenaModulesAccess: course.restrictedAthenaModulesAccess ?? false,
        timeZone: course.timeZone,
        courseInformationSharingConfiguration: course.courseInformationSharingConfiguration,
    };
}

/**
 * DTO for updating an existing course.
 * Mirrors the server-side CourseUpdateDTO to send only the necessary fields.
 */
export interface CourseUpdateDTO {
    // ID is required for update
    id: number;

    // Basic info
    title: string;
    shortName: string;
    description?: string;
    semester?: string;

    // Group names
    studentGroupName?: string;
    teachingAssistantGroupName?: string;
    editorGroupName?: string;
    instructorGroupName?: string;

    // Dates (as ISO strings for server)
    startDate?: string;
    endDate?: string;
    enrollmentStartDate?: string;
    enrollmentEndDate?: string;
    unenrollmentEndDate?: string;

    // Configuration flags
    testCourse: boolean;
    onlineCourse?: boolean;
    language?: Language;
    defaultProgrammingLanguage?: ProgrammingLanguage;

    // Complaint settings
    maxComplaints?: number;
    maxTeamComplaints?: number;
    maxComplaintTimeDays: number;
    maxRequestMoreFeedbackTimeDays: number;
    maxComplaintTextLimit: number;
    maxComplaintResponseTextLimit: number;

    // UI settings
    color?: string;
    courseIcon?: string;
    enrollmentEnabled?: boolean;
    enrollmentConfirmationMessage?: string;
    unenrollmentEnabled: boolean;
    courseInformationSharingMessagingCodeOfConduct?: string;

    // Course features
    faqEnabled: boolean;
    learningPathsEnabled: boolean;
    studentCourseAnalyticsDashboardEnabled: boolean;
    presentationScore?: number;
    maxPoints?: number;
    accuracyOfScores?: number;
    restrictedAthenaModulesAccess: boolean;
    timeZone?: string;
    courseInformationSharingConfiguration?: CourseInformationSharingConfiguration;
}

/**
 * Converts a Course entity to a CourseUpdateDTO for sending to the server.
 *
 * @param course the course entity to convert
 * @returns a CourseUpdateDTO with only the fields needed for update
 */
export function toCourseUpdateDTO(course: Course): CourseUpdateDTO {
    return {
        // ID is required for update
        id: course.id!,

        // Basic info
        title: course.title!,
        shortName: course.shortName!,
        description: course.description,
        semester: course.semester,

        // Group names
        studentGroupName: course.studentGroupName,
        teachingAssistantGroupName: course.teachingAssistantGroupName,
        editorGroupName: course.editorGroupName,
        instructorGroupName: course.instructorGroupName,

        // Dates (converted to ISO strings)
        startDate: convertDateFromClient(course.startDate),
        endDate: convertDateFromClient(course.endDate),
        enrollmentStartDate: convertDateFromClient(course.enrollmentStartDate),
        enrollmentEndDate: convertDateFromClient(course.enrollmentEndDate),
        unenrollmentEndDate: convertDateFromClient(course.unenrollmentEndDate),

        // Configuration flags
        testCourse: course.testCourse ?? false,
        onlineCourse: course.onlineCourse,
        language: course.language,
        defaultProgrammingLanguage: course.defaultProgrammingLanguage,

        // Complaint settings
        maxComplaints: course.maxComplaints,
        maxTeamComplaints: course.maxTeamComplaints,
        maxComplaintTimeDays: course.maxComplaintTimeDays ?? 7,
        maxRequestMoreFeedbackTimeDays: course.maxRequestMoreFeedbackTimeDays ?? 7,
        maxComplaintTextLimit: course.maxComplaintTextLimit ?? 2000,
        maxComplaintResponseTextLimit: course.maxComplaintResponseTextLimit ?? 2000,

        // UI settings
        color: course.color,
        courseIcon: course.courseIcon,
        enrollmentEnabled: course.enrollmentEnabled,
        enrollmentConfirmationMessage: course.enrollmentConfirmationMessage,
        unenrollmentEnabled: course.unenrollmentEnabled ?? false,
        courseInformationSharingMessagingCodeOfConduct: course.courseInformationSharingMessagingCodeOfConduct,

        // Course features
        faqEnabled: course.faqEnabled ?? false,
        learningPathsEnabled: course.learningPathsEnabled ?? false,
        studentCourseAnalyticsDashboardEnabled: course.studentCourseAnalyticsDashboardEnabled ?? false,
        presentationScore: course.presentationScore,
        maxPoints: course.maxPoints,
        accuracyOfScores: course.accuracyOfScores,
        restrictedAthenaModulesAccess: course.restrictedAthenaModulesAccess ?? false,
        timeZone: course.timeZone,
        courseInformationSharingConfiguration: course.courseInformationSharingConfiguration,
    };
}
