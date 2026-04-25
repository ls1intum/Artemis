import { toProgrammingExerciseTimelineUpdateDTO } from './programming-exercise-timeline-update-dto.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import dayjs from 'dayjs/esm';

describe('ProgrammingExerciseTimelineUpdateDTO', () => {
    describe('toProgrammingExerciseTimelineUpdateDTO', () => {
        it('should convert a programming exercise to a timeline update DTO', () => {
            const releaseDate = dayjs('2024-01-01T10:00:00');
            const startDate = dayjs('2024-01-02T10:00:00');
            const dueDate = dayjs('2024-01-15T23:59:00');
            const assessmentDueDate = dayjs('2024-01-20T23:59:00');
            const exampleSolutionDate = dayjs('2024-01-25T10:00:00');
            const buildAndTestDate = dayjs('2024-01-16T00:00:00');

            const exercise = {
                id: 42,
                releaseDate,
                startDate,
                dueDate,
                assessmentType: AssessmentType.SEMI_AUTOMATIC,
                assessmentDueDate,
                exampleSolutionPublicationDate: exampleSolutionDate,
                buildAndTestStudentSubmissionsAfterDueDate: buildAndTestDate,
            } as ProgrammingExercise;

            const dto = toProgrammingExerciseTimelineUpdateDTO(exercise);

            expect(dto.id).toBe(42);
            expect(dto.releaseDate).toBe(releaseDate.toJSON());
            expect(dto.startDate).toBe(startDate.toJSON());
            expect(dto.dueDate).toBe(dueDate.toJSON());
            expect(dto.assessmentType).toBe(AssessmentType.SEMI_AUTOMATIC);
            expect(dto.assessmentDueDate).toBe(assessmentDueDate.toJSON());
            expect(dto.exampleSolutionPublicationDate).toBe(exampleSolutionDate.toJSON());
            expect(dto.buildAndTestStudentSubmissionsAfterDueDate).toBe(buildAndTestDate.toJSON());
        });

        it('should handle undefined dates', () => {
            const exercise = {
                id: 1,
            } as ProgrammingExercise;

            const dto = toProgrammingExerciseTimelineUpdateDTO(exercise);

            expect(dto.id).toBe(1);
            expect(dto.releaseDate).toBeUndefined();
            expect(dto.startDate).toBeUndefined();
            expect(dto.dueDate).toBeUndefined();
            expect(dto.assessmentDueDate).toBeUndefined();
            expect(dto.exampleSolutionPublicationDate).toBeUndefined();
            expect(dto.buildAndTestStudentSubmissionsAfterDueDate).toBeUndefined();
        });
    });
});
