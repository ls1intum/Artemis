import { Exam } from 'app/entities/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import * as moment from 'moment';

/**
 * Calculates the individual end time based on the studentExam
 *
 * @param exam
 * @param studentExam
 * @return {moment}
 */
export const endTime = (exam: Exam, studentExam: StudentExam) => {
    if (!exam || !exam.endDate) {
        return undefined;
    }
    if (studentExam && studentExam.workingTime && exam.startDate) {
        return moment(exam.startDate).add(studentExam.workingTime, 'seconds');
    }
    return exam.endDate;
};

/**
 * Calculates the working time of the exam in seconds
 *
 * @param exam
 * @return {number | undefined}
 */
export const normalWorkingTime = (exam: Exam): number | undefined => {
    if (!exam || !exam.endDate || !exam.startDate) {
        return undefined;
    }
    return moment(exam.endDate).diff(exam.startDate, 'seconds');
};

/**
 * Determines whether or not the student has additional working time in the exam
 *
 * @param exam
 * @param studentExam
 * @return {boolean | undefined}
 */
export const hasAdditionalWorkingTime = (exam: Exam, studentExam: StudentExam): boolean | undefined => {
    if (exam && exam.endDate && exam.startDate && studentExam && studentExam.workingTime) {
        const personalEndDate = moment(exam.startDate).add(studentExam.workingTime, 'seconds');
        return personalEndDate.isAfter(exam.endDate);
    }
    return false;
};

/**
 * Calculates the additional working time in seconds
 *
 * @param exam
 * @param studentExam
 * @return {number} The additional working time in seconds
 */
export const getAdditionalWorkingTime = (exam: Exam, studentExam: StudentExam): number => {
    const personalEndDate = moment(exam.startDate).add(studentExam.workingTime, 'seconds');
    return personalEndDate.diff(exam.endDate, 'seconds');
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
