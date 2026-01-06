import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';

/**
 * Configuration entity for course enrollment settings.
 * This entity is lazily loaded from the Course entity.
 */
export class CourseEnrollmentConfiguration implements BaseEntity {
    public id?: number;
    public enrollmentEnabled?: boolean;
    public enrollmentStartDate?: dayjs.Dayjs;
    public enrollmentEndDate?: dayjs.Dayjs;
    public enrollmentConfirmationMessage?: string;
    public unenrollmentEnabled?: boolean;
    public unenrollmentEndDate?: dayjs.Dayjs;

    constructor() {
        this.enrollmentEnabled = false;
        this.unenrollmentEnabled = false;
    }
}
