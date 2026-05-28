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

export interface BaseCourseRequest {
    title: string;
    shortName: string;
    semester?: string;
    startDate?: dayjs.Dayjs;
    endDate?: dayjs.Dayjs;
    testCourse: boolean;
    reason: string;
}

export interface CourseRequest extends BaseCourseRequest {
    id?: number;
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
