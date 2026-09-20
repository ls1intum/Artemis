import dayjs from 'dayjs/esm';

/**
 * An exam as the course overview sidebar needs it, returned by
 * `GET api/exam/courses/{courseId}/exams-for-overview`.
 *
 * Deliberately not the full `Exam`: exercise groups, exercises, registered users and grading belong to the exam itself
 * and are loaded when a student opens it, not when the sidebar lists it.
 */
export interface ExamForOverview {
    id: number;
    title?: string;
    moduleNumber?: string;
    visibleDate?: dayjs.Dayjs;
    startDate?: dayjs.Dayjs;
    endDate?: dayjs.Dayjs;
    workingTime?: number;
    examMaxPoints?: number;
    testExam?: boolean;
}
