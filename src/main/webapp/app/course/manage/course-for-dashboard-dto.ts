import { Course } from 'app/entities/course.model';
import { ExerciseType, ExerciseTypeTOTAL } from 'app/entities/exercise.model';
import { ParticipantScoreDTO } from 'app/shared/participant-scores/participant-scores.service';
import { CourseScoresForStudentStatisticsDTO } from 'app/course/course-scores-for-student-statistics-dto';

export class CourseForDashboardDTO {
    course: Course;
    scoresPerExerciseType: Map<ExerciseType | ExerciseTypeTOTAL, CourseScoresForStudentStatisticsDTO>;
    participantScores: ParticipantScoreDTO[];

    constructor() {}
}
