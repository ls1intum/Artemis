import { User } from 'app/core/user/user.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { GradeStep } from 'app/entities/grade-step.model';
import { ExerciseTypeStatisticsMap } from 'app/course/course-scores/exercise-type-statistics-map';

export class CourseScoresStudentStatistics {
    user: User;
    participations: StudentParticipation[] = [];
    presentationScore = 0;
    presentationPoints = 0;
    numberOfParticipatedExercises = 0;
    numberOfSuccessfulExercises = 0;
    overallPoints = 0;
    // the index is the exercise id
    pointsPerExercise = new Map<number, number>();
    // the absolute number (sum) of points the students received per exercise type
    sumPointsPerExerciseType = new Map<ExerciseType, number>();
    // the relative number of points the students received per exercise type (divided by the max points per exercise type)
    scorePerExerciseType = new Map<ExerciseType, number>();
    // the points for all exercises of a specific type
    pointsPerExerciseType = new ExerciseTypeStatisticsMap();
    gradeStep?: GradeStep;

    constructor(user: User) {
        this.user = user;
        // initialize with 0 or empty string
        for (const exerciseType of Object.values(ExerciseType)) {
            this.sumPointsPerExerciseType.set(exerciseType, 0);
            this.scorePerExerciseType.set(exerciseType, 0);
            this.pointsPerExerciseType.set(exerciseType, new Map());
        }
    }
}
