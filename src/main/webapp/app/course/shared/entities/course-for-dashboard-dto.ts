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
     * Points the student earns from each exercise variant group, keyed by group id — already capped at the group's
     * maxPoints and adjusted for plagiarism verdicts on the server. Omitted when the course has no capped variant groups.
     */
    achievedPointsPerVariantGroup?: { [groupId: number]: number };
}

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) marker. */
export class ParticipationResultDTO {
    score?: number;
    rated?: boolean;
    participationId!: number;
}
