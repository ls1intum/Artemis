import { Course } from 'app/entities/course.model';
import { CourseScores } from 'app/course/course-scores/course-scores';
import { Result } from 'app/entities/result.model';
import { ExerciseType } from 'app/entities/exercise.model';

export class CourseForDashboardDTO {
    course: Course;

    totalScores: CourseScores;

    /**
     * Cannot use the proper Map type ({@link ScoresPerExerciseType}) here, because this CourseForDashboardDTO object represents the data returned from the server which is in JSON format and cannot contain a Map.
     * The {@link CourseManagementService#saveScoresInStorage} method converts the JSON object to a {@link ScoresPerExerciseType} object when the data is returned from the server and saved in the {@link ScoresStorageService}.
     */
    scoresPerExerciseType: { [key in ExerciseType]: CourseScores };

    participationResults: Result[];

    constructor() {}
}
