import dayjs from 'dayjs/esm';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseTimelineUpdateDTO, toProgrammingExerciseTimelineUpdateDTO } from './programming-exercise-timeline-update-dto.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

describe('ProgrammingExerciseTimelineUpdateDTO Model', () => {
    const baseDate = dayjs('2025-06-01T10:00:00.000Z');

    describe('toProgrammingExerciseTimelineUpdateDTO', () => {
        it('should convert a programming exercise to a timeline update DTO', () => {
            const exercise = {
                id: 5,
                releaseDate: baseDate,
                startDate: baseDate.add(1, 'day'),
                dueDate: baseDate.add(7, 'days'),
                assessmentType: AssessmentType.SEMI_AUTOMATIC,
                assessmentDueDate: baseDate.add(14, 'days'),
                exampleSolutionPublicationDate: baseDate.add(21, 'days'),
                buildAndTestStudentSubmissionsAfterDueDate: baseDate.add(8, 'days'),
            } as ProgrammingExercise;

            const dto: ProgrammingExerciseTimelineUpdateDTO = toProgrammingExerciseTimelineUpdateDTO(exercise);

            expect(dto.id).toBe(5);
            expect(dto.releaseDate).toBe(baseDate.toJSON());
            expect(dto.startDate).toBe(baseDate.add(1, 'day').toJSON());
            expect(dto.dueDate).toBe(baseDate.add(7, 'days').toJSON());
            expect(dto.assessmentType).toBe(AssessmentType.SEMI_AUTOMATIC);
            expect(dto.assessmentDueDate).toBe(baseDate.add(14, 'days').toJSON());
            expect(dto.exampleSolutionPublicationDate).toBe(baseDate.add(21, 'days').toJSON());
            expect(dto.buildAndTestStudentSubmissionsAfterDueDate).toBe(baseDate.add(8, 'days').toJSON());
        });

        it('should handle undefined dates gracefully', () => {
            const exercise = {
                id: 10,
            } as ProgrammingExercise;

            const dto = toProgrammingExerciseTimelineUpdateDTO(exercise);

            expect(dto.id).toBe(10);
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
