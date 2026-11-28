import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';
import { Course } from 'app/course/shared/entities/course.model';
import type { ExamForImportList } from 'app/openapi/model/exam-for-import-list';

/**
 * Converts an exam of the import table, as the server sends it, into an {@link Exam} with its course.
 *
 * The result holds plain objects, not new Exam() / new Course(): the constructors add defaults the server never sent.
 *
 * @param dto the generated exam of the import list
 * @returns the exam carrying only the fields the endpoint sends
 */
export function toExamForImport(dto: ExamForImportList): Exam {
    const course: Course | undefined = dto.course && { id: dto.course.id, title: dto.course.title, testCourse: dto.course.testCourse };
    return { id: dto.id, title: dto.title, examMode: ExamMode[dto.examMode], course };
}
