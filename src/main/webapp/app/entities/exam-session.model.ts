import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';
import { StudentExam } from './student-exam.model';

export enum SuspiciousSessionReason {
    SAME_IP_ADDRESS = 'SAME_IP_ADDRESS',
    SAME_BROWSER_FINGERPRINT = 'SAME_BROWSER_FINGERPRINT',
    SAME_USER_AGENT = 'SAME_USER_AGENT',
    NOT_SAME_IP_ADDRESS = 'NOT_SAME_IP_ADDRESS',
    NOT_SAME_BROWSER_FINGERPRINT = 'NOT_SAME_BROWSER_FINGERPRINT',
    NOT_SAME_USER_AGENT = 'NOT_SAME_USER_AGENT',
}
export function toReadableString(reason: SuspiciousSessionReason): string {
    switch (reason) {
        case SuspiciousSessionReason.SAME_IP_ADDRESS:
            return 'Same IP address but different exams';
        case SuspiciousSessionReason.SAME_BROWSER_FINGERPRINT:
            return 'Same browser fingerprint but different exams';
        case SuspiciousSessionReason.SAME_USER_AGENT:
            return 'Same user agent but different exams';
        case SuspiciousSessionReason.NOT_SAME_IP_ADDRESS:
            return 'Not the same IP address for the same exam ';
        case SuspiciousSessionReason.NOT_SAME_BROWSER_FINGERPRINT:
            return 'Not the same browser fingerprint for the same exam';
        case SuspiciousSessionReason.NOT_SAME_USER_AGENT:
            return 'Not the same user agent for the same exam';
        default:
            return 'Unknown reason';
    }
}

export class ExamSession implements BaseEntity {
    public id?: number;
    public studentExam?: StudentExam;
    public sessionToken?: string;
    public userAgent?: string;
    public browserFingerprintHash?: string;
    public instanceId?: string;
    public ipAddress?: string;
    public initialSession?: boolean;
    public createdBy?: string;
    public lastModifiedBy?: string;
    public createdDate?: dayjs.Dayjs;
    public lastModifiedDate?: Date;
    public suspiciousReasons: SuspiciousSessionReason[] = [];
}

export class SuspiciousExamSessions {
    examSessions: ExamSession[] = [];
}
