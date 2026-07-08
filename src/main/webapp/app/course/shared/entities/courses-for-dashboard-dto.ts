import { CourseForDashboardDTO } from 'app/course/shared/entities/course-for-dashboard-dto';
import { Exam } from 'app/exam/shared/entities/exam.model';

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class CoursesForDashboardDTO {
    courses!: CourseForDashboardDTO[];
    activeExams!: Exam[];
}
