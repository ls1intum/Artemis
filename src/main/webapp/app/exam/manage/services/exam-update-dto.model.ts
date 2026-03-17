import { Exam } from 'app/exam/shared/entities/exam.model';
import { convertDateFromClient } from 'app/shared/util/date.utils';

/**
 * DTO for updating exams.
 * Matches the server-side ExamUpdateDTO record structure.
 * Only includes the fields that the server expects, avoiding unnecessary data transfer.
 */
export interface ExamUpdateDTO {
    id?: number;
    title: string;
    testExam: boolean;
    examWithAttendanceCheck: boolean;
    visibleDate?: string;
    startDate?: string;
    endDate?: string;
    publishResultsDate?: string;
    examStudentReviewStart?: string;
    examStudentReviewEnd?: string;
    gracePeriod?: number;
    workingTime: number;
    startText?: string;
    endText?: string;
    confirmationStartText?: string;
    confirmationEndText?: string;
    examMaxPoints?: number;
    randomizeExerciseOrder?: boolean;
    numberOfExercisesInExam?: number;
    numberOfCorrectionRoundsInExam?: number;
    examiner?: string;
    moduleNumber?: string;
    courseName?: string;
    exampleSolutionPublicationDate?: string;
    channelName?: string;
}

/**
 * Converts an Exam entity to an ExamUpdateDTO.
 * This ensures only the required data is sent to the server,
 * reducing network payload and avoiding issues with circular references.
 *
 * @param exam the exam to convert
 * @returns the corresponding DTO
 */
export function toExamUpdateDTO(exam: Exam): ExamUpdateDTO {
    return {
        id: exam.id,
        title: exam.title!,
        testExam: exam.testExam ?? false,
        examWithAttendanceCheck: exam.examWithAttendanceCheck ?? false,
        visibleDate: convertDateFromClient(exam.visibleDate),
        startDate: convertDateFromClient(exam.startDate),
        endDate: convertDateFromClient(exam.endDate),
        publishResultsDate: convertDateFromClient(exam.publishResultsDate),
        examStudentReviewStart: convertDateFromClient(exam.examStudentReviewStart),
        examStudentReviewEnd: convertDateFromClient(exam.examStudentReviewEnd),
        gracePeriod: exam.gracePeriod,
        workingTime: exam.workingTime ?? 0,
        startText: exam.startText,
        endText: exam.endText,
        confirmationStartText: exam.confirmationStartText,
        confirmationEndText: exam.confirmationEndText,
        examMaxPoints: exam.examMaxPoints,
        randomizeExerciseOrder: exam.randomizeExerciseOrder,
        numberOfExercisesInExam: exam.numberOfExercisesInExam,
        numberOfCorrectionRoundsInExam: exam.numberOfCorrectionRoundsInExam,
        examiner: exam.examiner,
        moduleNumber: exam.moduleNumber,
        courseName: exam.courseName,
        exampleSolutionPublicationDate: convertDateFromClient(exam.exampleSolutionPublicationDate),
        channelName: exam.channelName,
    };
}
