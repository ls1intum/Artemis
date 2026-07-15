import dayjs from 'dayjs/esm';

export interface PresentationAssessment {
    id?: number;
    title?: string;
    description?: string;
    maxPoints?: number;
    resultPoints?: number;
    presentationDate?: dayjs.Dayjs;
    courseId?: number;
}
