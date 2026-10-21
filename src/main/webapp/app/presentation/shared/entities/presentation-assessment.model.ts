import dayjs from 'dayjs/esm';
import { User } from 'app/account/user/user.model';

export interface PresentationAssessment {
    id?: number;
    title?: string;
    description?: string;
    maxPoints?: number;
    courseId?: number;
    exerciseId?: number;
    exerciseTitle?: string;
}

export interface PresentationAssessmentInstance {
    id?: number;
    presentationDate?: dayjs.Dayjs;
    resultPoints?: number | null;
    student?: Pick<User, 'login' | 'name' | 'firstName' | 'lastName' | 'email'>;
    language?: string;
    mode?: PresentationAssessmentMode;
    location?: string;
    meetingLink?: string;
    remark?: string;
}

export interface PresentationAssessmentInstanceRequest {
    id?: number;
    presentationDate: dayjs.Dayjs;
    resultPoints?: number | null;
    studentLogin: string;
    language: string;
    mode: PresentationAssessmentMode;
    location?: string;
    meetingLink?: string;
    remark?: string;
}

export interface PresentationAssessmentInstancesCreate {
    presentationDate: dayjs.Dayjs;
    resultPoints?: number | null;
    studentLogins: string[];
    language: string;
    mode: PresentationAssessmentMode;
    location?: string;
    meetingLink?: string;
    remark?: string;
}

export interface PresentationAssessmentStudentRow {
    presentationAssessment: PresentationAssessment;
    instance: PresentationAssessmentInstance;
}

export interface PresentationAssessmentStudentRowsRequest {
    page: number;
    size: number;
    sortField: 'studentLogin' | 'presentationTitle' | 'presentationDate' | 'resultPoints';
    direction: 'ASC' | 'DESC';
    assessmentId?: number;
    assessed?: boolean;
    linkedToExercise?: boolean;
    searchTerm?: string;
}

export interface PresentationAssessmentStatistics {
    totalCount: number;
    assessedCount: number;
}

export enum PresentationAssessmentMode {
    ONLINE = 'ONLINE',
    IN_PERSON = 'IN_PERSON',
}
