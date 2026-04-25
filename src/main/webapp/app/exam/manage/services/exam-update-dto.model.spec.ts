import dayjs from 'dayjs/esm';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { toExamUpdateDTO } from 'app/exam/manage/services/exam-update-dto.model';
import * as dateUtils from 'app/shared/util/date.utils';

describe('ExamUpdateDTO mapping', () => {
    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should convert a fully populated exam to DTO', () => {
        const convertSpy = jest.spyOn(dateUtils, 'convertDateFromClient').mockReturnValue('2024-01-01T00:00:00.000Z');

        const exam = new Exam();
        exam.id = 42;
        exam.title = 'Final Exam';
        exam.testExam = true;
        exam.examWithAttendanceCheck = true;
        exam.visibleDate = dayjs('2024-01-01');
        exam.startDate = dayjs('2024-01-02');
        exam.endDate = dayjs('2024-01-03');
        exam.publishResultsDate = dayjs('2024-01-10');
        exam.examStudentReviewStart = dayjs('2024-01-11');
        exam.examStudentReviewEnd = dayjs('2024-01-12');
        exam.gracePeriod = 180;
        exam.workingTime = 5400;
        exam.startText = 'Good luck!';
        exam.endText = 'Time is up!';
        exam.confirmationStartText = 'Confirm start';
        exam.confirmationEndText = 'Confirm end';
        exam.examMaxPoints = 100;
        exam.randomizeExerciseOrder = true;
        exam.numberOfExercisesInExam = 5;
        exam.numberOfCorrectionRoundsInExam = 2;
        exam.examiner = 'Prof. Smith';
        exam.moduleNumber = 'CS101';
        exam.courseName = 'Intro to CS';
        exam.exampleSolutionPublicationDate = dayjs('2024-02-01');
        exam.channelName = 'exam-channel';

        const dto = toExamUpdateDTO(exam);

        expect(dto.id).toBe(42);
        expect(dto.title).toBe('Final Exam');
        expect(dto.testExam).toBeTrue();
        expect(dto.examWithAttendanceCheck).toBeTrue();
        expect(dto.gracePeriod).toBe(180);
        expect(dto.workingTime).toBe(5400);
        expect(dto.startText).toBe('Good luck!');
        expect(dto.endText).toBe('Time is up!');
        expect(dto.confirmationStartText).toBe('Confirm start');
        expect(dto.confirmationEndText).toBe('Confirm end');
        expect(dto.examMaxPoints).toBe(100);
        expect(dto.randomizeExerciseOrder).toBeTrue();
        expect(dto.numberOfExercisesInExam).toBe(5);
        expect(dto.numberOfCorrectionRoundsInExam).toBe(2);
        expect(dto.examiner).toBe('Prof. Smith');
        expect(dto.moduleNumber).toBe('CS101');
        expect(dto.courseName).toBe('Intro to CS');
        expect(dto.channelName).toBe('exam-channel');
        // 7 date fields: visibleDate, startDate, endDate, publishResultsDate, examStudentReviewStart, examStudentReviewEnd, exampleSolutionPublicationDate
        expect(convertSpy).toHaveBeenCalledTimes(7);
    });

    it('should use default values when exam fields are undefined', () => {
        jest.spyOn(dateUtils, 'convertDateFromClient').mockReturnValue(undefined);

        const exam = new Exam();
        exam.title = 'Minimal Exam';

        const dto = toExamUpdateDTO(exam);

        expect(dto.title).toBe('Minimal Exam');
        expect(dto.testExam).toBeFalse();
        expect(dto.examWithAttendanceCheck).toBeFalse();
        expect(dto.workingTime).toBe(0);
        expect(dto.id).toBeUndefined();
        expect(dto.gracePeriod).toBeUndefined();
        expect(dto.startText).toBeUndefined();
        expect(dto.endText).toBeUndefined();
        expect(dto.confirmationStartText).toBeUndefined();
        expect(dto.confirmationEndText).toBeUndefined();
        expect(dto.examMaxPoints).toBe(1);
        expect(dto.randomizeExerciseOrder).toBeFalse();
        expect(dto.numberOfCorrectionRoundsInExam).toBe(1);
        expect(dto.channelName).toBeUndefined();
    });
});
