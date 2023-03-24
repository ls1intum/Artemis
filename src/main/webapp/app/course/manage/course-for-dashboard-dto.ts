import { Course } from 'app/entities/course.model';
import { CourseScores } from 'app/course/course-scores/course-scores';
import { Result } from 'app/entities/result.model';
import { ExerciseType } from 'app/entities/exercise.model';

export class CourseForDashboardDTO {
    course: Course;

    totalScores: CourseScores;
    scoresPerExerciseType: { [key in ExerciseType]: CourseScores };
    participationResults: Result[];

    constructor() {}
}
