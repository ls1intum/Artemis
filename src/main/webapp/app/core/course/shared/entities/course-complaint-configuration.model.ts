import { BaseEntity } from 'app/shared/model/base-entity';

/**
 * Configuration entity for course complaint and feedback request settings.
 * This entity is lazily loaded from the Course entity.
 */
export class CourseComplaintConfiguration implements BaseEntity {
    public id?: number;
    public maxComplaints?: number;
    public maxTeamComplaints?: number;
    public maxComplaintTimeDays?: number;
    public maxRequestMoreFeedbackTimeDays?: number;
    public maxComplaintTextLimit?: number;
    public maxComplaintResponseTextLimit?: number;

    constructor() {
        this.maxComplaints = 3;
        this.maxTeamComplaints = 3;
        this.maxComplaintTimeDays = 7;
        this.maxRequestMoreFeedbackTimeDays = 7;
        this.maxComplaintTextLimit = 2000;
        this.maxComplaintResponseTextLimit = 2000;
    }

    /**
     * @returns true if complaints are enabled for this course (maxComplaintTimeDays > 0)
     */
    get complaintsEnabled(): boolean {
        return (this.maxComplaintTimeDays ?? 0) > 0;
    }

    /**
     * @returns true if request more feedback is enabled for this course (maxRequestMoreFeedbackTimeDays > 0)
     */
    get requestMoreFeedbackEnabled(): boolean {
        return (this.maxRequestMoreFeedbackTimeDays ?? 0) > 0;
    }
}
