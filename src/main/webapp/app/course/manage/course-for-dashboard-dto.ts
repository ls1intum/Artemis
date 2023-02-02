import { Course } from 'app/entities/course.model';
import { CourseScoresDTO } from 'app/course/course-scores/course-scores-dto';
import { Result } from 'app/entities/result.model';

export class CourseForDashboardDTO {
    course: Course;
    scoresPerExerciseType: { [key: string]: CourseScoresDTO };
    participationResults: Result[];

    constructor() {}
}
