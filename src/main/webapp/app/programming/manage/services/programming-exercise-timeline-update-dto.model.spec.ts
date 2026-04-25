import dayjs from 'dayjs/esm';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { toProgrammingExerciseTimelineUpdateDTO } from './programming-exercise-timeline-update-dto.model';

describe('ProgrammingExerciseTimelineUpdateDTO', () => {
    describe('toProgrammingExerciseTimelineUpdateDTO', () => {
        it('should convert all timeline fields', () => {
            const exercise = {
                id: 1,
                type: 'programming',
                releaseDate: dayjs('2024-01-01'),
                startDate: dayjs('2024-01-02'),
                dueDate: dayjs('2024-01-10'),
                assessmentType: AssessmentType.AUTOMATIC,
                assessmentDueDate: dayjs('2024-01-15'),
                exampleSolutionPublicationDate: dayjs('2024-01-20'),
                buildAndTestStudentSubmissionsAfterDueDate: dayjs('2024-01-11'),
            } as ProgrammingExercise;

            const dto = toProgrammingExerciseTimelineUpdateDTO(exercise);

            expect(dto.id).toBe(1);
            expect(dto.releaseDate).toBeDefined();
            expect(dto.startDate).toBeDefined();
            expect(dto.dueDate).toBeDefined();
            expect(dto.assessmentType).toBe(AssessmentType.AUTOMATIC);
            expect(dto.assessmentDueDate).toBeDefined();
            expect(dto.exampleSolutionPublicationDate).toBeDefined();
            expect(dto.buildAndTestStudentSubmissionsAfterDueDate).toBeDefined();
        });

        it('should handle undefined optional fields', () => {
            const exercise = {
                id: 2,
                type: 'programming',
            } as ProgrammingExercise;

            const dto = toProgrammingExerciseTimelineUpdateDTO(exercise);

            expect(dto.id).toBe(2);
            expect(dto.releaseDate).toBeUndefined();
            expect(dto.startDate).toBeUndefined();
            expect(dto.dueDate).toBeUndefined();
            expect(dto.assessmentType).toBeUndefined();
            expect(dto.assessmentDueDate).toBeUndefined();
            expect(dto.exampleSolutionPublicationDate).toBeUndefined();
            expect(dto.buildAndTestStudentSubmissionsAfterDueDate).toBeUndefined();
        });
    });
});
