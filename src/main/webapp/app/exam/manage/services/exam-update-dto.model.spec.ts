import { toExamUpdateDTO } from './exam-update-dto.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import dayjs from 'dayjs/esm';

describe('ExamUpdateDTO', () => {
    describe('toExamUpdateDTO', () => {
        it('should convert an exam to an update DTO with all fields', () => {
            const visibleDate = dayjs('2024-01-01T10:00:00');
            const startDate = dayjs('2024-01-15T10:00:00');
            const endDate = dayjs('2024-01-15T12:00:00');
            const publishResultsDate = dayjs('2024-01-20T10:00:00');
            const reviewStart = dayjs('2024-01-21T10:00:00');
            const reviewEnd = dayjs('2024-01-22T10:00:00');
            const exampleSolutionDate = dayjs('2024-01-25T10:00:00');

            const exam: Exam = {
                id: 1,
                title: 'Test Exam',
                testExam: false,
                examWithAttendanceCheck: true,
                visibleDate,
                startDate,
                endDate,
                publishResultsDate,
                examStudentReviewStart: reviewStart,
                examStudentReviewEnd: reviewEnd,
                gracePeriod: 180,
                workingTime: 7200,
                startText: 'Welcome',
                endText: 'Goodbye',
                confirmationStartText: 'Confirm start',
                confirmationEndText: 'Confirm end',
                examMaxPoints: 100,
                randomizeExerciseOrder: true,
                numberOfExercisesInExam: 5,
                numberOfCorrectionRoundsInExam: 2,
                examiner: 'Prof. Test',
                moduleNumber: 'CS101',
                courseName: 'Intro to CS',
                exampleSolutionPublicationDate: exampleSolutionDate,
                channelName: 'exam-channel',
            } as Exam;

            const dto = toExamUpdateDTO(exam);

            expect(dto.id).toBe(1);
            expect(dto.title).toBe('Test Exam');
            expect(dto.testExam).toBeFalse();
            expect(dto.examWithAttendanceCheck).toBeTrue();
            expect(dto.visibleDate).toBe(visibleDate.toJSON());
            expect(dto.startDate).toBe(startDate.toJSON());
            expect(dto.endDate).toBe(endDate.toJSON());
            expect(dto.publishResultsDate).toBe(publishResultsDate.toJSON());
            expect(dto.examStudentReviewStart).toBe(reviewStart.toJSON());
            expect(dto.examStudentReviewEnd).toBe(reviewEnd.toJSON());
            expect(dto.gracePeriod).toBe(180);
            expect(dto.workingTime).toBe(7200);
            expect(dto.startText).toBe('Welcome');
            expect(dto.endText).toBe('Goodbye');
            expect(dto.confirmationStartText).toBe('Confirm start');
            expect(dto.confirmationEndText).toBe('Confirm end');
            expect(dto.examMaxPoints).toBe(100);
            expect(dto.randomizeExerciseOrder).toBeTrue();
            expect(dto.numberOfExercisesInExam).toBe(5);
            expect(dto.numberOfCorrectionRoundsInExam).toBe(2);
            expect(dto.examiner).toBe('Prof. Test');
            expect(dto.moduleNumber).toBe('CS101');
            expect(dto.courseName).toBe('Intro to CS');
            expect(dto.exampleSolutionPublicationDate).toBe(exampleSolutionDate.toJSON());
            expect(dto.channelName).toBe('exam-channel');
        });

        it('should use defaults for undefined boolean fields', () => {
            const exam: Exam = {
                title: 'Minimal Exam',
            } as Exam;

            const dto = toExamUpdateDTO(exam);

            expect(dto.testExam).toBeFalse();
            expect(dto.examWithAttendanceCheck).toBeFalse();
            expect(dto.workingTime).toBe(0);
        });

        it('should handle undefined dates', () => {
            const exam: Exam = {
                title: 'No Dates Exam',
            } as Exam;

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
});
