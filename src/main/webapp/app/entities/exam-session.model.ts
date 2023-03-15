import { StudentExam } from './student-exam.model';
import { BaseEntity } from 'app/shared/model/base-entity';

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
    public createdDate?: Date;
    public lastModifiedDate?: Date;
}
