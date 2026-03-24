import dayjs from 'dayjs/esm';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { toProgrammingExerciseTimelineUpdateDTO } from './programming-exercise-timeline-update-dto.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

describe('ProgrammingExerciseTimelineUpdateDTO', () => {
    describe('toProgrammingExerciseTimelineUpdateDTO', () => {
        it('should convert a programming exercise to a timeline update DTO', () => {
            const exercise = new ProgrammingExercise(undefined, undefined);
            exercise.id = 1;
            exercise.releaseDate = dayjs('2026-01-01T10:00:00Z');
            exercise.startDate = dayjs('2026-01-01T10:00:00Z');
            exercise.dueDate = dayjs('2026-01-15T23:59:00Z');
            exercise.assessmentType = AssessmentType.AUTOMATIC;
            exercise.assessmentDueDate = dayjs('2026-01-20T23:59:00Z');
            exercise.exampleSolutionPublicationDate = dayjs('2026-01-16T00:00:00Z');
            exercise.buildAndTestStudentSubmissionsAfterDueDate = dayjs('2026-01-16T00:00:00Z');

            const dto = toProgrammingExerciseTimelineUpdateDTO(exercise);

            expect(dto.id).toBe(1);
            expect(dto.releaseDate).toBe(exercise.releaseDate.toJSON());
            expect(dto.startDate).toBe(exercise.startDate.toJSON());
            expect(dto.dueDate).toBe(exercise.dueDate.toJSON());
            expect(dto.assessmentType).toBe(AssessmentType.AUTOMATIC);
            expect(dto.assessmentDueDate).toBe(exercise.assessmentDueDate.toJSON());
            expect(dto.exampleSolutionPublicationDate).toBe(exercise.exampleSolutionPublicationDate.toJSON());
            expect(dto.buildAndTestStudentSubmissionsAfterDueDate).toBe(exercise.buildAndTestStudentSubmissionsAfterDueDate.toJSON());
        });

        it('should handle undefined date fields', () => {
            const exercise = new ProgrammingExercise(undefined, undefined);
            exercise.id = 2;

            const dto = toProgrammingExerciseTimelineUpdateDTO(exercise);

            expect(dto.id).toBe(2);
            expect(dto.releaseDate).toBeUndefined();
            expect(dto.dueDate).toBeUndefined();
            expect(dto.assessmentDueDate).toBeUndefined();
        });
    });
});
