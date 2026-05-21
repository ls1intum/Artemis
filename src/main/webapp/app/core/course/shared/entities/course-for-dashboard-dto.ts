import { Course } from 'app/core/course/shared/entities/course.model';
import { CourseScores } from 'app/core/course/manage/course-scores/course-scores';

export class CourseForDashboardDTO {
    course: Course;

    totalScores: CourseScores;

    textScores: CourseScores;
    programmingScores: CourseScores;
    modelingScores: CourseScores;
    fileUploadScores: CourseScores;
    quizScores: CourseScores;

    participationResults: ParticipationResultDTO[];

    courseNotificationCount: number;
    irisEnabledInCourse?: boolean;
}

export class ParticipationResultDTO {
    score?: number;
    rated?: boolean;
    participationId: number;
}
