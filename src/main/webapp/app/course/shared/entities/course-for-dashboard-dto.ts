import type { Course } from 'app/course/shared/entities/course.model';
import type { CourseScores } from 'app/course/manage/course-scores/course-scores';
import { courseFromDashboardDTO } from 'app/course/shared/entities/course-content-response.dto';
import type { CourseDashboardDTO } from 'app/course/shared/entities/course-content-response.dto';

export interface CourseForDashboardResponseDTO extends Omit<CourseForDashboardDTO, 'course'> {
    course: CourseDashboardDTO;
}

export interface CourseForDashboardDTO {
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

    /**
     * Points the student earns per variant group, keyed by group id, already capped and plagiarism-adjusted by the
     * server. Absent or empty only when no group contributes.
     */
    achievedPointsPerVariantGroup?: { [groupId: number]: number };
}

export interface ParticipationResultDTO {
    score?: number;
    rated?: boolean;
    participationId: number;
}

export function courseForDashboardFromDTO(dto: CourseForDashboardResponseDTO): CourseForDashboardDTO {
    return {
        course: courseFromDashboardDTO(dto.course),
        totalScores: dto.totalScores,
        textScores: dto.textScores,
        programmingScores: dto.programmingScores,
        modelingScores: dto.modelingScores,
        fileUploadScores: dto.fileUploadScores,
        quizScores: dto.quizScores,
        participationResults: dto.participationResults,
        courseNotificationCount: dto.courseNotificationCount,
        irisEnabledInCourse: dto.irisEnabledInCourse,
        achievedPointsPerVariantGroup: dto.achievedPointsPerVariantGroup,
    };
}
