import dayjs from 'dayjs/esm';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamUpdateDTO, toExamUpdateDTO } from './exam-update-dto.model';

describe('Exam Update DTO Model', () => {
    const baseDate = dayjs('2025-07-15T09:00:00.000Z');

    describe('toExamUpdateDTO', () => {
        it('should convert a full exam entity to an update DTO', () => {
            const exam: Exam = {
                id: 10,
                title: 'Midterm Exam',
                testExam: false,
                examWithAttendanceCheck: true,
                visibleDate: baseDate.subtract(1, 'week'),
                startDate: baseDate,
                endDate: baseDate.add(2, 'hours'),
                publishResultsDate: baseDate.add(1, 'day'),
                examStudentReviewStart: baseDate.add(2, 'days'),
                examStudentReviewEnd: baseDate.add(3, 'days'),
                gracePeriod: 180,
                workingTime: 7200,
                startText: 'Good luck!',
                endText: 'Well done!',
                confirmationStartText: 'Please confirm',
                confirmationEndText: 'Thank you',
                examMaxPoints: 100,
                randomizeExerciseOrder: true,
                numberOfExercisesInExam: 5,
                numberOfCorrectionRoundsInExam: 2,
                examiner: 'Prof. Smith',
                moduleNumber: 'CS101',
                courseName: 'Intro to CS',
                exampleSolutionPublicationDate: baseDate.add(4, 'days'),
                channelName: 'exam-midterm',
            };

            const dto: ExamUpdateDTO = toExamUpdateDTO(exam);

            expect(dto.id).toBe(10);
            expect(dto.title).toBe('Midterm Exam');
            expect(dto.testExam).toBeFalse();
            expect(dto.examWithAttendanceCheck).toBeTrue();
            expect(dto.visibleDate).toBe(baseDate.subtract(1, 'week').toJSON());
            expect(dto.startDate).toBe(baseDate.toJSON());
            expect(dto.endDate).toBe(baseDate.add(2, 'hours').toJSON());
            expect(dto.publishResultsDate).toBe(baseDate.add(1, 'day').toJSON());
            expect(dto.examStudentReviewStart).toBe(baseDate.add(2, 'days').toJSON());
            expect(dto.examStudentReviewEnd).toBe(baseDate.add(3, 'days').toJSON());
            expect(dto.gracePeriod).toBe(180);
            expect(dto.workingTime).toBe(7200);
            expect(dto.startText).toBe('Good luck!');
            expect(dto.endText).toBe('Well done!');
            expect(dto.confirmationStartText).toBe('Please confirm');
            expect(dto.confirmationEndText).toBe('Thank you');
            expect(dto.examMaxPoints).toBe(100);
            expect(dto.randomizeExerciseOrder).toBeTrue();
            expect(dto.numberOfExercisesInExam).toBe(5);
            expect(dto.numberOfCorrectionRoundsInExam).toBe(2);
            expect(dto.examiner).toBe('Prof. Smith');
            expect(dto.moduleNumber).toBe('CS101');
            expect(dto.courseName).toBe('Intro to CS');
            expect(dto.exampleSolutionPublicationDate).toBe(baseDate.add(4, 'days').toJSON());
            expect(dto.channelName).toBe('exam-midterm');
        });

        it('should apply default values for undefined boolean and number fields', () => {
            const minimalExam: Exam = {
                id: 1,
                title: 'Minimal Exam',
            };

            const dto = toExamUpdateDTO(minimalExam);

            expect(dto.id).toBe(1);
            expect(dto.title).toBe('Minimal Exam');
            expect(dto.testExam).toBeFalse();
            expect(dto.examWithAttendanceCheck).toBeFalse();
            expect(dto.workingTime).toBe(0);
        });

        it('should handle undefined dates gracefully', () => {
            const examNoDate: Exam = {
                title: 'No Date Exam',
            };

            const dto = toExamUpdateDTO(examNoDate);

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
