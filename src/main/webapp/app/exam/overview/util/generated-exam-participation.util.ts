import dayjs from 'dayjs/esm';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';
import { ExamForOverview } from 'app/exam/shared/entities/exam-for-overview.model';
import { CourseForStudentExamDTO, ExamForStudentExamDTO, StudentExamDTO } from 'app/exam/shared/entities/student-exam-dto.model';
import type { ExamMode as GeneratedExamMode } from 'app/openapi/model/exam-mode';
import type { ExamForOverview as GeneratedExamForOverview } from 'app/openapi/model/exam-for-overview';
import type { ExamSidebarData } from 'app/openapi/model/exam-sidebar-data';
import type { StudentExam as GeneratedStudentExam } from 'app/openapi/model/student-exam';
import type { ExamForStudentExam } from 'app/openapi/model/exam-for-student-exam';
import type { CourseForStudentExam } from 'app/openapi/model/course-for-student-exam';

/**
 * Bridges the generated models of the exam participation endpoints and the exam classes the participation views read.
 * Generated models carry dates as ISO strings and enums as string unions; the views read dayjs dates and enums.
 */

/**
 * Maps the generated exam mode to the exam mode enum of the client.
 *
 * @param mode the mode the server sent
 * @returns the enum value, or undefined if the server sent none
 */
export function toExamMode(mode: GeneratedExamMode | undefined): ExamMode | undefined {
    switch (mode) {
        case 'REAL':
            return ExamMode.REAL;
        case 'TEST':
            return ExamMode.TEST;
        case 'TEST_WITH_SUPERVISED_SESSION':
            return ExamMode.TEST_WITH_SUPERVISED_SESSION;
        default:
            return undefined;
    }
}

/**
 * Converts the exams of the course overview. An entry without an id cannot be linked from the sidebar and is skipped;
 * the server always sends the id.
 *
 * @param exams the generated overview exams
 * @returns the exams with dayjs dates
 */
export function toExamsForOverview(exams: GeneratedExamForOverview[]): ExamForOverview[] {
    return exams.flatMap((exam) => {
        if (exam.id === undefined) {
            return [];
        }
        return [
            {
                id: exam.id,
                title: exam.title,
                moduleNumber: exam.moduleNumber,
                visibleDate: convertDateStringFromServer(exam.visibleDate),
                startDate: convertDateStringFromServer(exam.startDate),
                endDate: convertDateStringFromServer(exam.endDate),
                workingTime: exam.workingTime,
                examMaxPoints: exam.examMaxPoints,
                examMode: toExamMode(exam.examMode),
            },
        ];
    });
}

/**
 * Converts the sidebar data of a real exam into an exam with the fields the sidebar sends.
 *
 * @param data the generated sidebar data
 * @returns the exam with a dayjs start date
 */
export function toSidebarExam(data: ExamSidebarData): Exam {
    return {
        id: data.id,
        title: data.title,
        moduleNumber: data.moduleNumber,
        startDate: dayjs(data.startDate),
        workingTime: data.workingTime,
        examMaxPoints: data.examMaxPoints,
    };
}

function toCourseForStudentExam(course: CourseForStudentExam): CourseForStudentExamDTO | undefined {
    const { id, ...fields } = course;
    return id === undefined ? undefined : hydrate({ id }, fields);
}

function toExamForStudentExam(exam: ExamForStudentExam | undefined): ExamForStudentExamDTO | undefined {
    const examMode = toExamMode(exam?.examMode);
    if (!exam || exam.id === undefined || examMode === undefined || exam.workingTime === undefined) {
        return undefined;
    }
    return {
        id: exam.id,
        title: exam.title,
        examMode,
        workingTime: exam.workingTime,
        course: exam.course && toCourseForStudentExam(exam.course),
    };
}

/**
 * Converts the test exams of a user. An entry without its id or test run flag is skipped; the server always sends both.
 *
 * @param studentExams the generated student exams
 * @returns the student exams with dayjs dates
 */
export function toStudentExamDTOs(studentExams: GeneratedStudentExam[]): StudentExamDTO[] {
    return studentExams.flatMap((studentExam) => {
        if (studentExam.id === undefined || studentExam.testRun === undefined) {
            return [];
        }
        return [
            {
                id: studentExam.id,
                workingTime: studentExam.workingTime,
                started: studentExam.started,
                startedDate: convertDateStringFromServer(studentExam.startedDate),
                submitted: studentExam.submitted,
                submissionDate: convertDateStringFromServer(studentExam.submissionDate),
                testRun: studentExam.testRun,
                user: studentExam.user,
                exam: toExamForStudentExam(studentExam.exam),
            },
        ];
    });
}
