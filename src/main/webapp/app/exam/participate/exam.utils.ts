import { Exam } from 'app/entities/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import dayjs from 'dayjs/esm';

/**
 * Calculates the individual end time based on the studentExam
 *
 * @param exam
 * @param studentExam
 * @return {dayjs}
 */
export const endTime = (exam: Exam, studentExam: StudentExam) => {
    if (!exam) {
        return undefined;
    }
    if (studentExam && studentExam.workingTime && exam.startDate) {
        return dayjs(exam.startDate).add(studentExam.workingTime, 'seconds');
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
    return dayjs(exam.endDate).diff(exam.startDate, 'seconds');
};

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
