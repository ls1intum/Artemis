import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';
import { Course } from 'app/course/shared/entities/course.model';
import type { ExamForImportList } from 'app/openapi/model/exam-for-import-list';

/**
 * Converts an exam of the import table, as the server sends it, into an {@link Exam} with its course.
 *
 * @param dto the generated exam of the import list
 * @returns the exam carrying only the fields the endpoint sends
 */
export function toExamForImport(dto: ExamForImportList): Exam {
    const exam = new Exam();
    exam.id = dto.id;
    exam.title = dto.title;
    exam.examMode = dto.examMode === undefined ? undefined : ExamMode[dto.examMode];
    if (dto.course) {
        const course = new Course();
        course.id = dto.course.id;
        course.title = dto.course.title;
        course.testCourse = dto.course.testCourse;
        exam.course = course;
    }
    return exam;
}
