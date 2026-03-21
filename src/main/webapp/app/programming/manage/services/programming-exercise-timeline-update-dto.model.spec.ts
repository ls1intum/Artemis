import dayjs from 'dayjs/esm';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { toProgrammingExerciseTimelineUpdateDTO } from './programming-exercise-timeline-update-dto.model';

describe('toProgrammingExerciseTimelineUpdateDTO', () => {
    it('should convert a ProgrammingExercise to a timeline update DTO', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 42;
        exercise.releaseDate = dayjs('2024-01-01T10:00:00.000Z');
        exercise.startDate = dayjs('2024-01-02T10:00:00.000Z');
        exercise.dueDate = dayjs('2024-01-15T23:59:00.000Z');
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        exercise.assessmentDueDate = dayjs('2024-01-20T23:59:00.000Z');
        exercise.exampleSolutionPublicationDate = dayjs('2024-01-21T10:00:00.000Z');
        exercise.buildAndTestStudentSubmissionsAfterDueDate = dayjs('2024-01-16T00:00:00.000Z');

        const dto = toProgrammingExerciseTimelineUpdateDTO(exercise);

        expect(dto.id).toBe(42);
        expect(dto.releaseDate).toBe(exercise.releaseDate.toJSON());
        expect(dto.startDate).toBe(exercise.startDate.toJSON());
        expect(dto.dueDate).toBe(exercise.dueDate.toJSON());
        expect(dto.assessmentType).toBe(AssessmentType.SEMI_AUTOMATIC);
        expect(dto.assessmentDueDate).toBe(exercise.assessmentDueDate.toJSON());
        expect(dto.exampleSolutionPublicationDate).toBe(exercise.exampleSolutionPublicationDate.toJSON());
        expect(dto.buildAndTestStudentSubmissionsAfterDueDate).toBe(exercise.buildAndTestStudentSubmissionsAfterDueDate.toJSON());
    });

    it('should handle undefined date fields', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 1;

        const dto = toProgrammingExerciseTimelineUpdateDTO(exercise);

        expect(dto.id).toBe(1);
        expect(dto.releaseDate).toBeUndefined();
        expect(dto.dueDate).toBeUndefined();
        expect(dto.assessmentDueDate).toBeUndefined();
    });
});
