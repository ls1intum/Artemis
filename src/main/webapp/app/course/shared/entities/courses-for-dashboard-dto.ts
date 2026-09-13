import { CourseForDashboardDTO, CourseForDashboardResponseDTO, courseForDashboardFromDTO } from 'app/course/shared/entities/course-for-dashboard-dto';
import type { Exam } from 'app/exam/shared/entities/exam.model';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';

export interface CoursesForDashboardDTO {
    courses: CourseForDashboardDTO[];
    activeExams?: Exam[];
}

export interface CoursesForDashboardResponseDTO {
    courses?: CourseForDashboardResponseDTO[];
    activeExams?: ActiveExamForCourseDashboardDTO[];
}

export interface ActiveExamForCourseDashboardDTO {
    id: number;
    title: string;
    startDate: string;
    endDate: string;
    testExam: boolean;
    course: ActiveExamCourseReferenceDTO;
}

export interface ActiveExamCourseReferenceDTO {
    id: number;
    title: string;
}

export function coursesForDashboardFromDTO(dto: CoursesForDashboardResponseDTO): CoursesForDashboardDTO {
    return {
        courses: (dto.courses ?? []).map(courseForDashboardFromDTO),
        activeExams: (dto.activeExams ?? []).map((examDTO) =>
            hydrate({} as Exam, examDTO, {
                startDate: convertDateStringFromServer(examDTO.startDate),
                endDate: convertDateStringFromServer(examDTO.endDate),
            }),
        ),
    };
}
