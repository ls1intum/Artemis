import { Exam } from 'app/entities/exam/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import dayjs from 'dayjs/esm';
import { round } from 'app/shared/util/utils';
import { ServerDateService } from 'app/shared/server-date.service';

/**
 * Calculates the individual end time based on the studentExam
 *
 * @param exam
 * @param studentExam
 * @return {dayjs.Dayjs | undefined}
 */
export const endTime = (exam: Exam, studentExam: StudentExam): dayjs.Dayjs | undefined => {
    if (!exam) {
        return undefined;
    }
    if (studentExam && studentExam.workingTime && exam.startDate) {
        return dayjs(exam.startDate).add(studentExam.workingTime, 'seconds');
    }
    return exam.endDate;
};

/**
 * Calculates the working time of the exam in seconds.
 * If either the exam or the exam dates are not set, undefined is returned.
 *
 * @param exam
 * @return {number | undefined}
 */
export const examWorkingTime = (exam?: Exam): number | undefined => normalWorkingTime(exam?.startDate, exam?.endDate);

/**
 * Calculates the time between start and end date in seconds.
 * If either the start or end date is not set, undefined is returned.
 *
 * @param startDate
 * @param endDate
 * @return {number | undefined}
 */
export const normalWorkingTime = (startDate?: dayjs.Dayjs, endDate?: dayjs.Dayjs): number | undefined =>
    startDate && endDate ? dayjs(endDate).diff(startDate, 'seconds') : undefined;

/**
 * Calculates the additional working time in seconds
 *
 * @param exam
 * @param studentExam
 * @return {number} The additional working time in seconds
 */
export const getAdditionalWorkingTime = (exam: Exam, studentExam: StudentExam): number => {
    if (exam && exam.endDate && exam.startDate && studentExam && studentExam.workingTime) {
        const personalEndDate = dayjs(exam.startDate).add(studentExam.workingTime, 'seconds');
        return personalEndDate.diff(exam.endDate, 'seconds');
    }
    return 0;
};

/**
 * Calculates the relative difference in percent points between the regular working time of the exam and the individual working time for the student.
 *
 * E.g., for a regular working time of "1h" and a student working time of "1h30min" the difference is +50.
 * @param exam The exam which the student exam belongs to.
 * @param studentExamWorkingTime The individual working time of a student exam.
 * @return The relative working time extension in percent points rounded to two digits after the decimal separator.
 */
export const getRelativeWorkingTimeExtension = (exam: Exam, studentExamWorkingTime: number): number => {
    const regularExamDuration = examWorkingTime(exam)!;
    return round((studentExamWorkingTime / regularExamDuration - 1.0) * 100, 2);
};

/**
 * Determines if the exam spans multiple days
 *
 * @param exam
 * @param studentExam
 * @return {boolean}
 */
export const isExamOverMultipleDays = (exam: Exam, studentExam: StudentExam): boolean => {
    if (!exam || !exam.startDate || !exam.endDate) {
        return false;
    }
    const endDate = endTime(exam, studentExam)!;

    return !endDate.isSame(exam.startDate, 'day');
};

export function isExamResultPublished(isTestRun: boolean, exam: Exam | undefined, serverDateService: ServerDateService) {
    if (isTestRun) {
        return true;
    }

    return exam?.publishResultsDate && dayjs(exam.publishResultsDate).isBefore(serverDateService.now());
}
