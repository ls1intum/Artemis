import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';
import { StudentExam } from './student-exam.model';

export enum SuspiciousSessionReason {
    SAME_IP_ADDRESS = 'SAME_IP_ADDRESS',
    SAME_BROWSER_FINGERPRINT = 'SAME_BROWSER_FINGERPRINT',
    SAME_USER_AGENT = 'SAME_USER_AGENT',
}
export function toReadableString(reason: SuspiciousSessionReason): string {
    switch (reason) {
        case SuspiciousSessionReason.SAME_IP_ADDRESS:
            return 'Same IP address';
        case SuspiciousSessionReason.SAME_BROWSER_FINGERPRINT:
            return 'Same browser fingerprint';
        case SuspiciousSessionReason.SAME_USER_AGENT:
            return 'Same user agent';
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
