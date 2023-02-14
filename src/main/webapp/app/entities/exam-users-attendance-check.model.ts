import { BaseEntity } from 'app/shared/model/base-entity';

export class ExamUserAttendanceCheck implements BaseEntity {
    id?: number;
    studentImagePath: string;
    login: string;
    registrationNumber: string;
    signingImagePath: string;
    started: boolean;
    submitted: boolean;
}
