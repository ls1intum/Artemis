import { convertDateFromClient } from 'app/shared/util/date.utils';
import { Course, CourseInformationSharingConfiguration, Language } from './course.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';

/**
 * Common fields shared between CourseCreateDTO and CourseUpdateDTO.
 */
interface CommonCourseFields {
    title: string;
    shortName: string;
    description?: string;
    semester?: string;
    studentGroupName?: string;
    teachingAssistantGroupName?: string;
    editorGroupName?: string;
    instructorGroupName?: string;
    startDate?: string;
    endDate?: string;
    enrollmentStartDate?: string;
    enrollmentEndDate?: string;
    unenrollmentEndDate?: string;
    testCourse: boolean;
    onlineCourse?: boolean;
    language?: Language;
    defaultProgrammingLanguage?: ProgrammingLanguage;
    maxComplaints?: number;
    maxTeamComplaints?: number;
    maxComplaintTimeDays: number;
    maxRequestMoreFeedbackTimeDays: number;
    maxComplaintTextLimit: number;
    maxComplaintResponseTextLimit: number;
    color?: string;
    enrollmentEnabled?: boolean;
    enrollmentConfirmationMessage?: string;
    unenrollmentEnabled: boolean;
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
 * Maps common fields from a Course entity to the shared DTO fields.
 */
function mapCommonCourseFields(course: Course): CommonCourseFields {
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
        testCourse: course.testCourse ?? false,
        onlineCourse: course.onlineCourse,
        language: course.language,
        defaultProgrammingLanguage: course.defaultProgrammingLanguage,
        maxComplaints: course.maxComplaints,
        maxTeamComplaints: course.maxTeamComplaints,
        maxComplaintTimeDays: course.maxComplaintTimeDays ?? 7,
        maxRequestMoreFeedbackTimeDays: course.maxRequestMoreFeedbackTimeDays ?? 7,
        maxComplaintTextLimit: course.maxComplaintTextLimit ?? 2000,
        maxComplaintResponseTextLimit: course.maxComplaintResponseTextLimit ?? 2000,
        color: course.color,
        enrollmentEnabled: course.enrollmentEnabled,
        enrollmentConfirmationMessage: course.enrollmentConfirmationMessage,
        unenrollmentEnabled: course.unenrollmentEnabled ?? false,
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
 * DTO for creating a new course.
 * Mirrors the server-side CourseCreateDTO to send only the necessary fields.
 * Note: No 'id' field since this is for creation.
 */
export type CourseCreateDTO = CommonCourseFields;

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
export interface CourseUpdateDTO extends CommonCourseFields {
    id: number;
    courseIcon?: string;
    courseInformationSharingMessagingCodeOfConduct?: string;
}

/**
 * Converts a Course entity to a CourseUpdateDTO for sending to the server.
 *
 * @param course the course entity to convert
 * @returns a CourseUpdateDTO with only the fields needed for update
 */
export function toCourseUpdateDTO(course: Course): CourseUpdateDTO {
    return {
        ...mapCommonCourseFields(course),
        id: course.id!,
        courseIcon: course.courseIcon,
        courseInformationSharingMessagingCodeOfConduct: course.courseInformationSharingMessagingCodeOfConduct,
    };
}
