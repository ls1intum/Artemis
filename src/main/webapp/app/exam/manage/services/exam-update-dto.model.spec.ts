import dayjs from 'dayjs/esm';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { toExamUpdateDTO } from './exam-update-dto.model';

describe('toExamUpdateDTO', () => {
    it('should convert a fully populated exam to an update DTO', () => {
        const exam = new Exam();
        exam.id = 1;
        exam.title = 'Final Exam';
        exam.testExam = false;
        exam.examWithAttendanceCheck = true;
        exam.visibleDate = dayjs('2024-01-01T08:00:00.000Z');
        exam.startDate = dayjs('2024-01-15T09:00:00.000Z');
        exam.endDate = dayjs('2024-01-15T11:00:00.000Z');
        exam.publishResultsDate = dayjs('2024-01-20T12:00:00.000Z');
        exam.examStudentReviewStart = dayjs('2024-01-25T08:00:00.000Z');
        exam.examStudentReviewEnd = dayjs('2024-01-26T20:00:00.000Z');
        exam.gracePeriod = 180;
        exam.workingTime = 7200;
        exam.startText = 'Good luck!';
        exam.endText = 'Please submit.';
        exam.confirmationStartText = 'Confirm start';
        exam.confirmationEndText = 'Confirm end';
        exam.examMaxPoints = 100;
        exam.randomizeExerciseOrder = true;
        exam.numberOfExercisesInExam = 5;
        exam.numberOfCorrectionRoundsInExam = 2;
        exam.examiner = 'Prof. Smith';
        exam.moduleNumber = 'CS101';
        exam.courseName = 'Intro to CS';
        exam.exampleSolutionPublicationDate = dayjs('2024-02-01T10:00:00.000Z');
        exam.channelName = 'exam-channel';

        const dto = toExamUpdateDTO(exam);

        expect(dto.id).toBe(1);
        expect(dto.title).toBe('Final Exam');
        expect(dto.testExam).toBeFalse();
        expect(dto.examWithAttendanceCheck).toBeTrue();
        expect(dto.visibleDate).toBe(exam.visibleDate.toJSON());
        expect(dto.startDate).toBe(exam.startDate.toJSON());
        expect(dto.endDate).toBe(exam.endDate.toJSON());
        expect(dto.publishResultsDate).toBe(exam.publishResultsDate.toJSON());
        expect(dto.examStudentReviewStart).toBe(exam.examStudentReviewStart.toJSON());
        expect(dto.examStudentReviewEnd).toBe(exam.examStudentReviewEnd.toJSON());
        expect(dto.gracePeriod).toBe(180);
        expect(dto.workingTime).toBe(7200);
        expect(dto.startText).toBe('Good luck!');
        expect(dto.endText).toBe('Please submit.');
        expect(dto.confirmationStartText).toBe('Confirm start');
        expect(dto.confirmationEndText).toBe('Confirm end');
        expect(dto.examMaxPoints).toBe(100);
        expect(dto.randomizeExerciseOrder).toBeTrue();
        expect(dto.numberOfExercisesInExam).toBe(5);
        expect(dto.numberOfCorrectionRoundsInExam).toBe(2);
        expect(dto.examiner).toBe('Prof. Smith');
        expect(dto.moduleNumber).toBe('CS101');
        expect(dto.courseName).toBe('Intro to CS');
        expect(dto.exampleSolutionPublicationDate).toBe(exam.exampleSolutionPublicationDate.toJSON());
        expect(dto.channelName).toBe('exam-channel');
    });

    it('should apply defaults for undefined boolean/number fields', () => {
        const exam = new Exam();
        exam.id = 2;
        exam.title = 'Minimal Exam';

        const dto = toExamUpdateDTO(exam);

        expect(dto.id).toBe(2);
        expect(dto.title).toBe('Minimal Exam');
        expect(dto.testExam).toBeFalse();
        expect(dto.examWithAttendanceCheck).toBeFalse();
        expect(dto.workingTime).toBe(0);
    });

    it('should handle undefined date fields', () => {
        const exam = new Exam();
        exam.id = 3;
        exam.title = 'No Dates Exam';

        const dto = toExamUpdateDTO(exam);

        expect(dto.visibleDate).toBeUndefined();
        expect(dto.startDate).toBeUndefined();
        expect(dto.endDate).toBeUndefined();
        expect(dto.publishResultsDate).toBeUndefined();
        expect(dto.examStudentReviewStart).toBeUndefined();
        expect(dto.examStudentReviewEnd).toBeUndefined();
        expect(dto.exampleSolutionPublicationDate).toBeUndefined();
    });
});
