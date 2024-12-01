import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';
import { StudentExam } from '../student-exam.model';

export enum SuspiciousSessionReason {
    DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS = 'DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS',
    DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT = 'DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT',

    SAME_STUDENT_EXAM_DIFFERENT_IP_ADDRESSES = 'SAME_STUDENT_EXAM_DIFFERENT_IP_ADDRESSES',
    SAME_STUDENT_EXAM_DIFFERENT_BROWSER_FINGERPRINTS = 'SAME_STUDENT_EXAM_DIFFERENT_BROWSER_FINGERPRINTS',
    IP_ADDRESS_OUTSIDE_OF_RANGE = 'IP_ADDRESS_OUTSIDE_OF_RANGE',
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
    public suspiciousReasons: SuspiciousSessionReason[];
}

export class SuspiciousExamSessions {
    examSessions: ExamSession[];
}
export class SuspiciousSessionsAnalysisOptions {
    constructor(
        sameIpAddressDifferentStudentExams: boolean,
        sameBrowserFingerprintDifferentStudentExams: boolean,
        differentIpAddressesSameStudentExam: boolean,
        differentBrowserFingerprintsSameStudentExam: boolean,
        ipAddressOutsideOfRange: boolean,
        subnet?: string,
    ) {
        this.sameIpAddressDifferentStudentExams = sameIpAddressDifferentStudentExams;
        this.sameBrowserFingerprintDifferentStudentExams = sameBrowserFingerprintDifferentStudentExams;
        this.differentIpAddressesSameStudentExam = differentIpAddressesSameStudentExam;
        this.differentBrowserFingerprintsSameStudentExam = differentBrowserFingerprintsSameStudentExam;
        this.ipAddressOutsideOfRange = ipAddressOutsideOfRange;
        this.ipSubnet = subnet;
    }
    sameIpAddressDifferentStudentExams = false;
    sameBrowserFingerprintDifferentStudentExams = false;
    differentIpAddressesSameStudentExam = false;
    differentBrowserFingerprintsSameStudentExam = false;
    ipAddressOutsideOfRange = false;
    ipSubnet?: string;
}
