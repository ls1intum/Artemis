import { Course } from 'app/entities/course.model';
import { CourseScores } from 'app/course/course-scores/course-scores';
import { Exam } from 'app/entities/exam.model';

export class CourseForDashboardDTO {
    course: Course;

    totalScores: CourseScores;

    textScores: CourseScores;
    programmingScores: CourseScores;
    modelingScores: CourseScores;
    fileUploadScores: CourseScores;
    quizScores: CourseScores;

    participationResults: ParticipationResultDTO[];

    activeExams: Exam[];

    constructor() {}
}

export class ParticipationResultDTO {
    score?: number;
    rated?: boolean;
    participationId: number;
    constructor() {}
}
