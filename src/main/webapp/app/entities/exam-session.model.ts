import { BaseEntity } from 'app/shared/model/base-entity';
import { StudentExam } from './student-exam.model';

export class ExamSession implements BaseEntity {
    public id?: number;
    public studentExam?: StudentExam;
    public sessionToken?: string;
    public initialSession?: boolean;
}
