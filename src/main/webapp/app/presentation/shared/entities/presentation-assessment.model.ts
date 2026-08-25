import dayjs from 'dayjs/esm';

export interface PresentationAssessment {
    id?: number;
    title?: string;
    description?: string;
    maxPoints?: number;
    resultPoints?: number | null;
    presentationDate?: dayjs.Dayjs;
    courseId?: number;
    studentLogins?: string[];
    exerciseId?: number;
    exerciseTitle?: string;
    instances?: PresentationAssessmentInstance[];
}

export interface PresentationAssessmentInstance {
    id?: number;
    presentationDate?: dayjs.Dayjs;
    resultPoints?: number | null;
    studentLogins?: string[];
    language?: string;
    mode?: PresentationAssessmentMode;
    location?: string;
    meetingLink?: string;
    remark?: string;
}

export enum PresentationAssessmentMode {
    ONLINE = 'ONLINE',
    IN_PERSON = 'IN_PERSON',
}
