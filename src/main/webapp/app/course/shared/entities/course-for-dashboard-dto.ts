import { Course } from 'app/course/shared/entities/course.model';
import { CourseScores } from 'app/course/manage/course-scores/course-scores';

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class CourseForDashboardDTO {
    course!: Course;

    totalScores!: CourseScores;

    textScores!: CourseScores;
    programmingScores!: CourseScores;
    modelingScores!: CourseScores;
    fileUploadScores!: CourseScores;
    quizScores!: CourseScores;

    participationResults!: ParticipationResultDTO[];

    courseNotificationCount!: number;
    irisEnabledInCourse?: boolean;

    /**
     * Points the student earns per variant group, keyed by group id, already capped and plagiarism-adjusted by the
     * server. Absent or empty only when no group contributes.
     */
    achievedPointsPerVariantGroup?: { [groupId: number]: number };
}

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) marker. */
export class ParticipationResultDTO {
    score?: number;
    rated?: boolean;
    participationId!: number;
}
