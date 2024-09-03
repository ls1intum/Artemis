import { CourseForDashboardDTO } from 'app/course/manage/course-for-dashboard-dto';
import { Exam } from 'app/entities/exam/exam.model';

export class CoursesForDashboardDTO {
    courses: CourseForDashboardDTO[];
    activeExams: Exam[];

    constructor() {}
}
