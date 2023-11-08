import { BaseEntity } from 'app/shared/model/base-entity';

export class ExamUserAttendanceCheckDTO implements BaseEntity {
    id?: number;
    studentImagePath: string;
    login: string;
    registrationNumber: string;
    signingImagePath: string;
    started: boolean;
    submitted: boolean;
}
