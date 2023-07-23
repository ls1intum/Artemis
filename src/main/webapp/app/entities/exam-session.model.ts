import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';
import { StudentExam } from './student-exam.model';

enum SuspiciousSessionReason {
    SAME_IP_ADDRESS,
    SAME_BROWSER_FINGERPRINT,
    SAME_INSTANCE_ID,
    SAME_USER_AGENT,
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
    public reasons: SuspiciousSessionReason[] = [];
}

export class SuspiciousExamSessions {
    examSessions: ExamSession[] = [];
}
