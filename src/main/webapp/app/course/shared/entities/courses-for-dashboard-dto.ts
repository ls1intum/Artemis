import { CourseForDashboardDTO } from 'app/course/shared/entities/course-for-dashboard-dto';
import { Exam } from 'app/exam/shared/entities/exam.model';

export class CoursesForDashboardDTO {
    courses: CourseForDashboardDTO[];
    activeExams: Exam[];
}
