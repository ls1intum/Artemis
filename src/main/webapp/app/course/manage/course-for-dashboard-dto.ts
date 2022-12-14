import { Course } from 'app/entities/course.model';
import { ExerciseType, ExerciseTypeTOTAL } from 'app/entities/exercise.model';
import { CourseScoresDTO } from 'app/course/course-scores-dto';
import { Result } from 'app/entities/result.model';

export class CourseForDashboardDTO {
    course: Course;
    scoresPerExerciseType: Map<ExerciseType | ExerciseTypeTOTAL, CourseScoresDTO>;
    participationResults: Result[];

    constructor() {}
}
