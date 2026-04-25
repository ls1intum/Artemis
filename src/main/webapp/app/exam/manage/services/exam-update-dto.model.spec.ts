import dayjs from 'dayjs/esm';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { toExamUpdateDTO } from './exam-update-dto.model';

describe('ExamUpdateDTO', () => {
    describe('toExamUpdateDTO', () => {
        it('should convert an exam entity to an ExamUpdateDTO', () => {
            const exam: Exam = {
                id: 1,
                title: 'Test Exam',
                testExam: true,
                examWithAttendanceCheck: false,
                visibleDate: dayjs('2026-01-01T10:00:00Z'),
                startDate: dayjs('2026-01-02T10:00:00Z'),
                endDate: dayjs('2026-01-02T12:00:00Z'),
                gracePeriod: 180,
                workingTime: 7200,
                startText: 'Good luck!',
                endText: 'The end',
                examMaxPoints: 100,
                randomizeExerciseOrder: true,
                numberOfExercisesInExam: 5,
                numberOfCorrectionRoundsInExam: 2,
                channelName: 'exam-channel',
            };

            const dto = toExamUpdateDTO(exam);

            expect(dto.id).toBe(1);
            expect(dto.title).toBe('Test Exam');
            expect(dto.testExam).toBeTrue();
            expect(dto.examWithAttendanceCheck).toBeFalse();
            expect(dto.visibleDate).toBe(exam.visibleDate!.toJSON());
            expect(dto.startDate).toBe(exam.startDate!.toJSON());
            expect(dto.endDate).toBe(exam.endDate!.toJSON());
            expect(dto.gracePeriod).toBe(180);
            expect(dto.workingTime).toBe(7200);
            expect(dto.startText).toBe('Good luck!');
            expect(dto.endText).toBe('The end');
            expect(dto.examMaxPoints).toBe(100);
            expect(dto.randomizeExerciseOrder).toBeTrue();
            expect(dto.numberOfExercisesInExam).toBe(5);
            expect(dto.numberOfCorrectionRoundsInExam).toBe(2);
            expect(dto.channelName).toBe('exam-channel');
        });

        it('should handle undefined optional fields', () => {
            const exam: Exam = {
                id: 2,
                title: 'Minimal Exam',
            };

            const dto = toExamUpdateDTO(exam);

            expect(dto.id).toBe(2);
            expect(dto.title).toBe('Minimal Exam');
            expect(dto.testExam).toBeFalse();
            expect(dto.examWithAttendanceCheck).toBeFalse();
            expect(dto.workingTime).toBe(0);
            expect(dto.visibleDate).toBeUndefined();
            expect(dto.startDate).toBeUndefined();
            expect(dto.endDate).toBeUndefined();
            expect(dto.publishResultsDate).toBeUndefined();
        });
    });
});
