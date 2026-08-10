import dayjs from 'dayjs/esm';
import { Course, CourseInformationSharingConfiguration, Language } from 'app/course/shared/entities/course.model';

/**
 * The course data the course overview container needs, returned by `GET api/course/courses/{courseId}/for-overview`.
 *
 * Flat scalars rather than a course object: the server projects exactly these fields, so the contract does not move
 * whenever the Course entity does. Carries no exercises, lectures, exams, participations or scores — each tab loads
 * what it needs, and which tabs to offer comes from the available-tabs endpoint.
 */
export interface CourseForOverviewDTO {
    id: number;
    title?: string;
    shortName?: string;
    description?: string;
    semester?: string;
    startDate?: string;
    endDate?: string;
    enrollmentEnabled?: boolean;
    enrollmentStartDate?: string;
    enrollmentEndDate?: string;
    enrollmentConfirmationMessage?: string;
    unenrollmentEnabled?: boolean;
    unenrollmentEndDate?: string;
    color?: string;
    courseIcon?: string;
    testCourse?: boolean;
    onlineCourse?: boolean;
    language?: Language;
    timeZone?: string;
    courseInformationSharingConfiguration?: CourseInformationSharingConfiguration;
    courseInformationSharingMessagingCodeOfConduct?: string;
    maxPoints?: number;
    accuracyOfScores?: number;
    presentationScore?: number;
    maxComplaints?: number;
    maxTeamComplaints?: number;
    maxComplaintTimeDays?: number;
    maxComplaintTextLimit?: number;
    maxComplaintResponseTextLimit?: number;
    maxRequestMoreFeedbackTimeDays?: number;
    complaintsEnabled?: boolean;
    requestMoreFeedbackEnabled?: boolean;
    learningPathsEnabled?: boolean;
    trainingEnabled?: boolean;
    /** Server-derived, kept on the contract because the endpoint sends them; the client Course has no such fields. */
    gradeRelevant?: boolean;
    dataRetentionHold?: boolean;
    restrictedAthenaModulesAccess?: boolean;
    onboardingDone?: boolean;
    courseNotificationCount: number;
}

/**
 * Builds the {@link Course} the overview works with from the projected fields.
 *
 * The client keeps a Course because more than twenty components across every module read the stored course; converting
 * all of them is a separate change. What matters here is that the wire format no longer depends on the entity.
 *
 * Dates arrive as strings and are converted the same way {@link CourseManagementService} converts them elsewhere.
 *
 * @param dto the projected course
 */
export function courseFromOverviewDTO(dto: CourseForOverviewDTO): Course {
    const course = new Course();
    course.id = dto.id;
    course.title = dto.title;
    course.shortName = dto.shortName;
    course.description = dto.description;
    course.semester = dto.semester;
    course.startDate = dto.startDate ? dayjs(dto.startDate) : undefined;
    course.endDate = dto.endDate ? dayjs(dto.endDate) : undefined;
    course.enrollmentEnabled = dto.enrollmentEnabled;
    course.enrollmentStartDate = dto.enrollmentStartDate ? dayjs(dto.enrollmentStartDate) : undefined;
    course.enrollmentEndDate = dto.enrollmentEndDate ? dayjs(dto.enrollmentEndDate) : undefined;
    course.enrollmentConfirmationMessage = dto.enrollmentConfirmationMessage;
    course.unenrollmentEnabled = dto.unenrollmentEnabled;
    course.unenrollmentEndDate = dto.unenrollmentEndDate ? dayjs(dto.unenrollmentEndDate) : undefined;
    course.color = dto.color;
    course.courseIcon = dto.courseIcon;
    course.testCourse = dto.testCourse;
    course.onlineCourse = dto.onlineCourse;
    course.language = dto.language;
    course.timeZone = dto.timeZone;
    course.courseInformationSharingConfiguration = dto.courseInformationSharingConfiguration;
    course.courseInformationSharingMessagingCodeOfConduct = dto.courseInformationSharingMessagingCodeOfConduct;
    course.maxPoints = dto.maxPoints;
    course.accuracyOfScores = dto.accuracyOfScores;
    course.presentationScore = dto.presentationScore;
    course.maxComplaints = dto.maxComplaints;
    course.maxTeamComplaints = dto.maxTeamComplaints;
    course.maxComplaintTimeDays = dto.maxComplaintTimeDays;
    course.maxComplaintTextLimit = dto.maxComplaintTextLimit;
    course.maxComplaintResponseTextLimit = dto.maxComplaintResponseTextLimit;
    course.maxRequestMoreFeedbackTimeDays = dto.maxRequestMoreFeedbackTimeDays;
    course.complaintsEnabled = dto.complaintsEnabled;
    course.requestMoreFeedbackEnabled = dto.requestMoreFeedbackEnabled;
    course.learningPathsEnabled = dto.learningPathsEnabled;
    course.trainingEnabled = dto.trainingEnabled;
    course.restrictedAthenaModulesAccess = dto.restrictedAthenaModulesAccess;
    course.onboardingDone = dto.onboardingDone;
    return course;
}
