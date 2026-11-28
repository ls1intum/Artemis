import { BaseEntity } from 'app/foundation/model/base-entity';
import dayjs from 'dayjs/esm';
import { StudentExam } from './student-exam.model';

export enum SuspiciousSessionReason {
    DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS = 'DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS',
    DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT = 'DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT',

    SAME_STUDENT_EXAM_DIFFERENT_IP_ADDRESSES = 'SAME_STUDENT_EXAM_DIFFERENT_IP_ADDRESSES',
    SAME_STUDENT_EXAM_DIFFERENT_BROWSER_FINGERPRINTS = 'SAME_STUDENT_EXAM_DIFFERENT_BROWSER_FINGERPRINTS',
    IP_ADDRESS_OUTSIDE_OF_RANGE = 'IP_ADDRESS_OUTSIDE_OF_RANGE',
}
export interface ExamSession extends BaseEntity {
    id?: number;
    studentExam?: StudentExam;
    sessionToken?: string;
    userAgent?: string;
    browserFingerprintHash?: string;
    instanceId?: string;
    ipAddress?: string;
    initialSession?: boolean;
    createdBy?: string;
    lastModifiedBy?: string;
    createdDate?: dayjs.Dayjs;
    lastModifiedDate?: Date;
    suspiciousReasons: SuspiciousSessionReason[];
}
