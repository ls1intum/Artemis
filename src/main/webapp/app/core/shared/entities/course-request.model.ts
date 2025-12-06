import dayjs from 'dayjs/esm';

import { User } from 'app/core/user/user.model';

export enum CourseRequestStatus {
    PENDING = 'PENDING',
    ACCEPTED = 'ACCEPTED',
    REJECTED = 'REJECTED',
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
    requester?: User;
    createdCourseId?: number;
}
