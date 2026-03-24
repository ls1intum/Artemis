import dayjs from 'dayjs/esm';
import * as dateUtils from 'app/shared/util/date.utils';
import { toProgrammingExerciseTimelineUpdateDTO } from 'app/programming/manage/services/programming-exercise-timeline-update-dto.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

describe('ProgrammingExerciseTimelineUpdateDTO mapping', () => {
    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should convert a programming exercise to a timeline update DTO', () => {
        const convertSpy = jest.spyOn(dateUtils, 'convertDateFromClient').mockReturnValue('2024-06-01T10:00:00.000Z' as any);

        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 5;
        exercise.releaseDate = dayjs('2024-05-01');
        exercise.startDate = dayjs('2024-05-15');
        exercise.dueDate = dayjs('2024-06-01');
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        exercise.assessmentDueDate = dayjs('2024-06-15');
        exercise.exampleSolutionPublicationDate = dayjs('2024-06-20');
        exercise.buildAndTestStudentSubmissionsAfterDueDate = dayjs('2024-06-02');

        const dto = toProgrammingExerciseTimelineUpdateDTO(exercise);

        expect(dto.id).toBe(5);
        expect(dto.assessmentType).toBe(AssessmentType.SEMI_AUTOMATIC);
        expect(convertSpy).toHaveBeenCalledTimes(6);
    });

    it('should handle undefined dates', () => {
        jest.spyOn(dateUtils, 'convertDateFromClient').mockReturnValue(undefined);

        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 7;

        const dto = toProgrammingExerciseTimelineUpdateDTO(exercise);

        expect(dto.id).toBe(7);
        expect(dto.releaseDate).toBeUndefined();
        expect(dto.dueDate).toBeUndefined();
        expect(dto.assessmentType).toBeUndefined();
    });
});
