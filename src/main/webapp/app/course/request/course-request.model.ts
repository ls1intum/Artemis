import dayjs from 'dayjs/esm';

export enum CourseRequestStatus {
    PENDING = 'PENDING',
    ACCEPTED = 'ACCEPTED',
    REJECTED = 'REJECTED',
}

/**
 * Lightweight requester information for course request display.
 */
export interface CourseRequestRequester {
    id?: number;
    login?: string;
    name?: string;
    email?: string;
}

/**
 * Fields shared between the outgoing create/update payload and the incoming server representation. Kept apart
 * from the two so each can give startDate and endDate their own optionality.
 */
interface CourseRequestCommon {
    title: string;
    shortName: string;
    semester?: string;
    testCourse: boolean;
    reason: string;
}

/**
 * The create/update payload. The form guarantees startDate and endDate, so they are required here.
 */
export interface BaseCourseRequest extends CourseRequestCommon {
    startDate: dayjs.Dayjs;
    endDate: dayjs.Dayjs;
}

/**
 * The server's representation of a course request. startDate and endDate stay optional: a request created before
 * the two fields became mandatory can still arrive without them.
 */
export interface CourseRequest extends CourseRequestCommon {
    id?: number;
    startDate?: dayjs.Dayjs;
    endDate?: dayjs.Dayjs;
    status?: CourseRequestStatus;
    createdDate?: dayjs.Dayjs;
    processedDate?: dayjs.Dayjs;
    decisionReason?: string;
    requester?: CourseRequestRequester;
    createdCourseId?: number;
    instructorCourseCount?: number;
}

export interface CourseRequestsAdminOverview {
    pendingRequests: CourseRequest[];
    decidedRequests: CourseRequest[];
    totalDecidedCount: number;
}
