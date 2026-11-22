import { Exam } from 'app/exam/shared/entities/exam.model';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { hydrate } from 'app/foundation/util/deep-clone.util';

/**
 * Bridges the generated exam models and the exam class the management views read.
 *
 * The exam endpoints answer with different shapes (the full exam, the exam with exercise groups, the slim exam of the
 * assessment dashboard and so on). Each carries a subset of the fields below; the converted exam carries exactly the
 * fields of its response and none of the defaults of a new exam.
 */

/** The date fields that every generated exam shape may carry as ISO strings, and the id that all of them carry. */
export interface ExamSource {
    id?: number;
    visibleDate?: string;
    startDate?: string;
    endDate?: string;
    publishResultsDate?: string;
    examStudentReviewStart?: string;
    examStudentReviewEnd?: string;
    exampleSolutionPublicationDate?: string;
    examSummaryPublicationDate?: string;
}

/**
 * Converts a generated exam response into an {@link Exam}. Every field the server sent is kept, the exercise groups and
 * the course as they came. Only the exam dates become dayjs dates: the exercise and course dates stay as the server
 * sent them, as the hand-written service left them.
 *
 * @param source any generated shape of an exam
 * @returns the exam, with the fields that the response lacks left undefined
 */
export function toExam(source: ExamSource): Exam {
    // A plain object, not new Exam(): the constructor adds defaults such as examMaxPoints = 1 that the server never sent.
    const exam: Exam = {};
    hydrate(exam, source);
    exam.visibleDate = convertDateStringFromServer(source.visibleDate);
    exam.startDate = convertDateStringFromServer(source.startDate);
    exam.endDate = convertDateStringFromServer(source.endDate);
    exam.publishResultsDate = convertDateStringFromServer(source.publishResultsDate);
    exam.examStudentReviewStart = convertDateStringFromServer(source.examStudentReviewStart);
    exam.examStudentReviewEnd = convertDateStringFromServer(source.examStudentReviewEnd);
    exam.exampleSolutionPublicationDate = convertDateStringFromServer(source.exampleSolutionPublicationDate);
    exam.examSummaryPublicationDate = convertDateStringFromServer(source.examSummaryPublicationDate);
    return exam;
}

/**
 * Copies the rows of a generated list response into an array of the client type that they stand for. The server sends
 * every field that the client type requires; the generated model marks all of them optional.
 *
 * @param rows the rows of the response
 * @returns a deep copy of the rows typed as the client type
 */
export function toRows<T extends object>(rows: object[]): T[] {
    const copy: T[] = [];
    hydrate(copy, rows);
    return copy;
}
