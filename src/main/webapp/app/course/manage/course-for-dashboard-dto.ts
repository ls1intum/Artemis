import { Course } from 'app/entities/course.model';
import { ExerciseType, ExerciseTypeTOTAL } from 'app/entities/exercise.model';
import { CourseScoresForStudentStatisticsDTO } from 'app/course/course-scores-for-student-statistics-dto';
import { Result } from 'app/entities/result.model';

export class CourseForDashboardDTO {
    course: Course;
    scoresPerExerciseType: Map<ExerciseType | ExerciseTypeTOTAL, CourseScoresForStudentStatisticsDTO>;
    participationResults: Result[];

    constructor() {}
}
