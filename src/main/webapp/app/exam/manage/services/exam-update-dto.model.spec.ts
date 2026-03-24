import dayjs from 'dayjs/esm';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { toExamUpdateDTO } from './exam-update-dto.model';

describe('ExamUpdateDTO', () => {
    describe('toExamUpdateDTO', () => {
        it('should convert a full exam to DTO', () => {
            const exam: Exam = {
                id: 1,
                title: 'Final Exam',
                testExam: false,
                examWithAttendanceCheck: true,
                visibleDate: dayjs('2024-01-01'),
                startDate: dayjs('2024-01-10'),
                endDate: dayjs('2024-01-10T02:00:00'),
                publishResultsDate: dayjs('2024-01-15'),
                examStudentReviewStart: dayjs('2024-01-16'),
                examStudentReviewEnd: dayjs('2024-01-20'),
                gracePeriod: 180,
                workingTime: 7200,
                startText: 'Good luck!',
                endText: 'Please submit.',
                confirmationStartText: 'Confirm start',
                confirmationEndText: 'Confirm end',
                examMaxPoints: 100,
                randomizeExerciseOrder: true,
                numberOfExercisesInExam: 5,
                numberOfCorrectionRoundsInExam: 2,
                examiner: 'Prof. Test',
                moduleNumber: 'CS101',
                courseName: 'Intro to CS',
                exampleSolutionPublicationDate: dayjs('2024-01-25'),
                channelName: 'exam-channel',
            } as Exam;

            const dto = toExamUpdateDTO(exam);

            expect(dto.id).toBe(1);
            expect(dto.title).toBe('Final Exam');
            expect(dto.testExam).toBeFalse();
            expect(dto.examWithAttendanceCheck).toBeTrue();
            expect(dto.visibleDate).toBeDefined();
            expect(dto.startDate).toBeDefined();
            expect(dto.endDate).toBeDefined();
            expect(dto.publishResultsDate).toBeDefined();
            expect(dto.examStudentReviewStart).toBeDefined();
            expect(dto.examStudentReviewEnd).toBeDefined();
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
            expect(dto.examiner).toBe('Prof. Test');
            expect(dto.moduleNumber).toBe('CS101');
            expect(dto.courseName).toBe('Intro to CS');
            expect(dto.exampleSolutionPublicationDate).toBeDefined();
            expect(dto.channelName).toBe('exam-channel');
        });

        it('should use defaults for undefined boolean and number fields', () => {
            const exam = {
                id: 2,
                title: 'Test Exam',
            } as Exam;

            const dto = toExamUpdateDTO(exam);

            expect(dto.id).toBe(2);
            expect(dto.title).toBe('Test Exam');
            expect(dto.testExam).toBeFalse();
            expect(dto.examWithAttendanceCheck).toBeFalse();
            expect(dto.workingTime).toBe(0);
        });

        it('should handle undefined dates', () => {
            const exam = {
                id: 3,
                title: 'Minimal Exam',
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
