import { Course } from 'app/entities/course.model';
import { CourseScoresDTO } from 'app/course/course-scores/course-scores-dto';
import { Result } from 'app/entities/result.model';
import { ExerciseType, ExerciseTypeTOTAL } from 'app/entities/exercise.model';

export class CourseForDashboardDTO {
    course: Course;
    scoresPerExerciseType: { [key in ExerciseType | ExerciseTypeTOTAL]: CourseScoresDTO };
    participationResults: Result[];

    constructor() {}
}
