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
 * from the two so each can give startDate, endDate and semester their own optionality.
 */
interface CourseRequestCommon {
    title: string;
    shortName: string;
    testCourse: boolean;
    reason: string;
}

/**
 * The create/update payload. The form guarantees startDate, endDate and semester, so they are required here.
 * The server rejects a payload that omits any of them, so leaving them optional would only let a programmatic
 * caller build a request that cannot succeed.
 */
export interface BaseCourseRequest extends CourseRequestCommon {
    startDate: dayjs.Dayjs;
    endDate: dayjs.Dayjs;
    semester: string;
}

/**
 * The server's representation of a course request. startDate, endDate and semester stay optional: a request
 * created before the three fields became mandatory can still arrive without them.
 */
export interface CourseRequest extends CourseRequestCommon {
    id?: number;
    startDate?: dayjs.Dayjs;
    endDate?: dayjs.Dayjs;
    semester?: string;
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
