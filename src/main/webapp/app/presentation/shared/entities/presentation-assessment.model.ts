import dayjs from 'dayjs/esm';
import { User } from 'app/account/user/user.model';

export interface PresentationAssessment {
    id?: number;
    title?: string;
    description?: string;
    gradingMethod?: PresentationGradingMethod;
    maxPoints?: number;
    courseId?: number;
    exerciseId?: number;
    exerciseTitle?: string;
    rubricCriteria?: PresentationRubricCriterion[];
}

export interface PresentationAssessmentInstance {
    id?: number;
    presentationDate?: dayjs.Dayjs;
    resultPoints?: number | null;
    passed?: boolean | null;
    rubricScore?: number | null;
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
    passed?: boolean | null;
    studentLogin: string;
    language: string;
    mode: PresentationAssessmentMode;
    location?: string;
    meetingLink?: string;
    remark?: string;
    ratings?: PresentationRubricRating[];
}

export interface PresentationAssessmentInstancesCreate {
    presentationDate: dayjs.Dayjs;
    resultPoints?: number | null;
    passed?: boolean | null;
    studentLogins: string[];
    language: string;
    mode: PresentationAssessmentMode;
    location?: string;
    meetingLink?: string;
    remark?: string;
    ratings?: PresentationRubricRating[];
}

export interface PresentationRubricLevel {
    id?: number;
    description?: string;
}

export interface PresentationRubricCriterion {
    id?: number;
    name: string;
    description?: string;
    weight: number;
    scale: PresentationRubricScale;
    levels: PresentationRubricLevel[];
}

export interface PresentationRubric {
    criteria: PresentationRubricCriterion[];
    assessedInstanceCount: number;
}

export interface PresentationRubricRating {
    criterionId: number;
    levelId: number;
    comment?: string;
}

export interface PresentationAssessmentStudentRow {
    presentationAssessment: PresentationAssessment;
    instance: PresentationAssessmentInstance;
}

export interface PresentationAssessmentStudentRowsRequest {
    page: number;
    size: number;
    sortField: 'studentLogin' | 'presentationTitle' | 'presentationDate';
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

export enum PresentationGradingMethod {
    PASS_FAIL = 'PASS_FAIL',
    SCORE = 'SCORE',
    RUBRIC = 'RUBRIC',
}

export enum PresentationRubricScale {
    THREE_POINT = 'THREE_POINT',
    FIVE_POINT = 'FIVE_POINT',
}
