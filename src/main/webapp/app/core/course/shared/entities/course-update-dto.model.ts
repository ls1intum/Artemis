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

function valueOrDefault<T>(value: T | undefined, defaultValue: T): T {
    return value ?? defaultValue;
}

/**
 * Extracts the common fields shared between create and update DTOs.
 */
function mapCommonCourseFields(course: Course): Omit<CourseCreateDTO, never> {
    return {
        title: course.title!,
        shortName: course.shortName!,
        description: course.description,
        semester: course.semester,
        studentGroupName: course.studentGroupName,
        teachingAssistantGroupName: course.teachingAssistantGroupName,
        editorGroupName: course.editorGroupName,
        instructorGroupName: course.instructorGroupName,
        startDate: convertDateFromClient(course.startDate),
        endDate: convertDateFromClient(course.endDate),
        enrollmentStartDate: convertDateFromClient(course.enrollmentStartDate),
        enrollmentEndDate: convertDateFromClient(course.enrollmentEndDate),
        unenrollmentEndDate: convertDateFromClient(course.unenrollmentEndDate),
        testCourse: valueOrDefault(course.testCourse, false),
        onlineCourse: course.onlineCourse,
        language: course.language,
        defaultProgrammingLanguage: course.defaultProgrammingLanguage,
        maxComplaints: course.maxComplaints,
        maxTeamComplaints: course.maxTeamComplaints,
        maxComplaintTimeDays: valueOrDefault(course.maxComplaintTimeDays, 7),
        maxRequestMoreFeedbackTimeDays: valueOrDefault(course.maxRequestMoreFeedbackTimeDays, 7),
        maxComplaintTextLimit: valueOrDefault(course.maxComplaintTextLimit, 2000),
        maxComplaintResponseTextLimit: valueOrDefault(course.maxComplaintResponseTextLimit, 2000),
        color: course.color,
        enrollmentEnabled: course.enrollmentEnabled,
        enrollmentConfirmationMessage: course.enrollmentConfirmationMessage,
        unenrollmentEnabled: valueOrDefault(course.unenrollmentEnabled, false),
        faqEnabled: valueOrDefault(course.faqEnabled, false),
        learningPathsEnabled: valueOrDefault(course.learningPathsEnabled, false),
        studentCourseAnalyticsDashboardEnabled: valueOrDefault(course.studentCourseAnalyticsDashboardEnabled, false),
        presentationScore: course.presentationScore,
        maxPoints: course.maxPoints,
        accuracyOfScores: course.accuracyOfScores,
        restrictedAthenaModulesAccess: valueOrDefault(course.restrictedAthenaModulesAccess, false),
        timeZone: course.timeZone,
        courseInformationSharingConfiguration: course.courseInformationSharingConfiguration,
    };
}

/**
 * Converts a Course entity to a CourseCreateDTO for sending to the server.
 *
 * @param course the course entity to convert
 * @returns a CourseCreateDTO with only the fields needed for creation
 */
export function toCourseCreateDTO(course: Course): CourseCreateDTO {
    return mapCommonCourseFields(course);
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
    return Object.assign(mapCommonCourseFields(course), {
        id: course.id!,
        courseIcon: course.courseIcon,
        courseInformationSharingMessagingCodeOfConduct: course.courseInformationSharingMessagingCodeOfConduct,
    }) as CourseUpdateDTO;
}
