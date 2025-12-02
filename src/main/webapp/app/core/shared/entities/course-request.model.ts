import dayjs from 'dayjs/esm';

import { User } from 'app/core/user/user.model';

export enum CourseRequestStatus {
    PENDING = 'PENDING',
    ACCEPTED = 'ACCEPTED',
    REJECTED = 'REJECTED',
}

export interface CourseRequest {
    id?: number;
    title: string;
    shortName: string;
    semester?: string;
    startDate?: dayjs.Dayjs;
    endDate?: dayjs.Dayjs;
    testCourse: boolean;
    reason: string;
    status?: CourseRequestStatus;
    createdDate?: dayjs.Dayjs;
    processedDate?: dayjs.Dayjs;
    decisionReason?: string;
    requester?: User;
    createdCourseId?: number;
}

export type NewCourseRequest = Omit<CourseRequest, 'id' | 'status' | 'createdDate' | 'processedDate' | 'decisionReason' | 'requester' | 'createdCourseId'>;
