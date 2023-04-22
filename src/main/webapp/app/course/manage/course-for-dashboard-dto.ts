import { Course } from 'app/entities/course.model';
import { CourseScores } from 'app/course/course-scores/course-scores';
import { Result } from 'app/entities/result.model';
import { ScoresPerExerciseType } from 'app/entities/exercise.model';

export class CourseForDashboardDTO {
    course: Course;

    totalScores: CourseScores;
    scoresPerExerciseType: ScoresPerExerciseType;
    participationResults: Result[];

    constructor() {}
}
