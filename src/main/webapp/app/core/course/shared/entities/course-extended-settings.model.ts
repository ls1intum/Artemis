import { BaseEntity } from 'app/shared/model/base-entity';

/**
 * Extended settings entity for course.
 * Contains less frequently accessed fields like description and code of conduct.
 * This entity is lazily loaded from the Course entity.
 */
export class CourseExtendedSettings implements BaseEntity {
    public id?: number;
    public description?: string;
    public messagingCodeOfConduct?: string;
    public courseArchivePath?: string;

    constructor() {}

    /**
     * @returns true if the course has been archived
     */
    hasCourseArchive(): boolean {
        return !!this.courseArchivePath;
    }
}
