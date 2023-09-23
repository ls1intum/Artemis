import { Course } from 'app/entities/course.model';
import { CourseScores } from 'app/course/course-scores/course-scores';

export class CourseForDashboardDTO {
    course: Course;

    totalScores: CourseScores;

    textScores: CourseScores;
    programmingScores: CourseScores;
    modelingScores: CourseScores;
    fileUploadScores: CourseScores;
    quizScores: CourseScores;

    participationResults: ParticipationResultDTO[];

    constructor() {}
}

export class ParticipationResultDTO {
    score?: number;
    rated?: boolean;
    participationId: number;
    constructor() {}
}
