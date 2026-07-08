import { BaseEntity } from 'app/foundation/model/base-entity';

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class ExamUserAttendanceCheckDTO implements BaseEntity {
    id?: number;
    studentImagePath!: string;
    login!: string;
    registrationNumber!: string;
    signingImagePath!: string;
    started!: boolean;
    submitted!: boolean;
}
