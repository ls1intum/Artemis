import { Course } from 'app/entities/course.model';
import { CourseScores } from 'app/course/course-scores/course-scores';
import { Result } from 'app/entities/result.model';

export class CourseForDashboardDTO {
    course: Course;

    totalScores: CourseScores;

    textScores: CourseScores;
    programmingScores: CourseScores;
    modelingScores: CourseScores;
    fileUploadScores: CourseScores;
    quizScores: CourseScores;

    participationResults: Result[];

    constructor() {}
}
