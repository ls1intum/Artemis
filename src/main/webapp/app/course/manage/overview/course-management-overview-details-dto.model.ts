export class CourseManagementOverviewDetailsDto {
    public id?: number;
    public title?: string;
    public testCourse?: boolean;
    public semester?: string;
    public shortName?: string;
    public color?: string;
    public isAtLeastTutor: boolean;
    public isAtLeastInstructor: boolean;
    public numberOfStudents?: number;
    public numberOfTeachingAssistants?: number;
    public numberOfInstructors?: number;
    public studentGroupName?: string;
    public teachingAssistantGroupName?: string;
    public instructorGroupName?: string;

    constructor() {
        this.isAtLeastTutor = false;
        this.isAtLeastInstructor = false;
    }
}
