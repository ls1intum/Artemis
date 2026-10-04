import dayjs from 'dayjs/esm';
import { Course, CourseInformationSharingConfiguration } from 'app/course/shared/entities/course.model';

/**
 * The course data the course overview container needs, returned by `GET api/course/courses/{courseId}/for-overview`.
 *
 * Flat scalars projected straight out of the database rather than a serialized course, so the contract does not move
 * when the Course entity does. Every field has a reader on an overview path; fields the entity carries but the overview
 * never reads are deliberately absent. Content collections are absent too — each tab loads what it needs, and which
 * tabs to offer comes from the available-tabs endpoint.
 */
export interface CourseForOverviewDTO {
    id: number;
    title?: string;
    startDate?: string;
    endDate?: string;
    color?: string;
    courseIcon?: string;
    testCourse?: boolean;
    onlineCourse?: boolean;
    enrollmentEnabled?: boolean;
    enrollmentEndDate?: string;
    unenrollmentEnabled?: boolean;
    unenrollmentEndDate?: string;
    courseInformationSharingConfiguration?: CourseInformationSharingConfiguration;
    courseInformationSharingMessagingCodeOfConduct?: string;
    accuracyOfScores?: number;
    presentationScore?: number;
    complaintsEnabled?: boolean;
    maxComplaints?: number;
    maxTeamComplaints?: number;
    maxComplaintTimeDays?: number;
    maxComplaintTextLimit?: number;
    maxComplaintResponseTextLimit?: number;
    requestMoreFeedbackEnabled?: boolean;
    maxRequestMoreFeedbackTimeDays?: number;
    athenaGradingFeedbackEnabled?: boolean;
    athenaFormativeFeedbackEnabled?: boolean;
    courseNotificationCount: number;
}

/**
 * Builds the {@link Course} the overview works with from the projected fields.
 *
 * The client keeps a Course because more than twenty components across every module read the stored course; converting
 * all of them is a separate change. What matters here is that the wire format no longer depends on the entity.
 *
 * @param dto the projected course
 */
export function courseFromOverviewDTO(dto: CourseForOverviewDTO): Course {
    const course = new Course();
    course.id = dto.id;
    course.title = dto.title;
    course.startDate = dto.startDate ? dayjs(dto.startDate) : undefined;
    course.endDate = dto.endDate ? dayjs(dto.endDate) : undefined;
    course.color = dto.color;
    course.courseIcon = dto.courseIcon;
    course.testCourse = dto.testCourse;
    course.onlineCourse = dto.onlineCourse;
    course.enrollmentEnabled = dto.enrollmentEnabled;
    course.enrollmentEndDate = dto.enrollmentEndDate ? dayjs(dto.enrollmentEndDate) : undefined;
    course.unenrollmentEnabled = dto.unenrollmentEnabled;
    course.unenrollmentEndDate = dto.unenrollmentEndDate ? dayjs(dto.unenrollmentEndDate) : undefined;
    course.courseInformationSharingConfiguration = dto.courseInformationSharingConfiguration;
    course.courseInformationSharingMessagingCodeOfConduct = dto.courseInformationSharingMessagingCodeOfConduct;
    course.accuracyOfScores = dto.accuracyOfScores;
    course.presentationScore = dto.presentationScore;
    course.complaintsEnabled = dto.complaintsEnabled;
    course.maxComplaints = dto.maxComplaints;
    course.maxTeamComplaints = dto.maxTeamComplaints;
    course.maxComplaintTimeDays = dto.maxComplaintTimeDays;
    course.maxComplaintTextLimit = dto.maxComplaintTextLimit;
    course.maxComplaintResponseTextLimit = dto.maxComplaintResponseTextLimit;
    course.requestMoreFeedbackEnabled = dto.requestMoreFeedbackEnabled;
    course.maxRequestMoreFeedbackTimeDays = dto.maxRequestMoreFeedbackTimeDays;
    course.athenaGradingFeedbackEnabled = dto.athenaGradingFeedbackEnabled;
    course.athenaFormativeFeedbackEnabled = dto.athenaFormativeFeedbackEnabled;
    return course;
}
