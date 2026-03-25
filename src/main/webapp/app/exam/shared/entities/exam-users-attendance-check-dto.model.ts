import { ExamUserDTO } from 'app/exam/shared/entities/exam-user-dto.model';

export class ExamUserAttendanceCheckDTO extends ExamUserDTO {
    id?: number;
    studentImagePath: string;
    started: boolean;
    submitted: boolean;
}
