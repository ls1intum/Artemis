import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseScores } from 'app/course/manage/course-scores/course-scores';
import { ParticipationResultDTO } from 'app/course/shared/entities/course-for-dashboard-dto';

/**
 * The exercise data of a course overview, returned by `GET api/course/courses/{courseId}/exercises-for-overview`.
 *
 * Only the exercises tab and the statistics tab need this, so it is loaded when one of them is opened rather than on
 * every course entry.
 *
 * Instantiated and/or deserialized from server data; fields are populated after construction, hence the
 * definite-assignment (!) markers.
 */
export class CourseExercisesForOverviewDTO {
    exercises!: Exercise[];

    totalScores!: CourseScores;

    textScores!: CourseScores;
    programmingScores!: CourseScores;
    modelingScores!: CourseScores;
    fileUploadScores!: CourseScores;
    quizScores!: CourseScores;

    participationResults!: ParticipationResultDTO[];

    /**
     * Points the student earns per variant group, keyed by group id, already capped and plagiarism-adjusted by the
     * server. Absent or empty only when no group contributes.
     */
    achievedPointsPerVariantGroup?: { [groupId: number]: number };
}
