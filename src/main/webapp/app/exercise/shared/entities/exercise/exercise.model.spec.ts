import { CompetencyExerciseLink, CourseCompetency } from 'app/atlas/shared/entities/competency.model';
import { Exercise, getExerciseCompetencies } from './exercise.model';

describe('Exercise Model', () => {
    describe('getExerciseCompetencies', () => {
        it('should return empty array when exercise has no competencyLinks', () => {
            const exercise = { id: 1 } as Exercise;

            const result = getExerciseCompetencies(exercise);

            expect(result).toEqual([]);
        });

        it('should return empty array when competencyLinks is undefined', () => {
            const exercise = { id: 1, competencyLinks: undefined } as Exercise;

            const result = getExerciseCompetencies(exercise);

            expect(result).toEqual([]);
        });

        it('should return empty array when competencyLinks is empty', () => {
            const exercise = { id: 1, competencyLinks: [] as CompetencyExerciseLink[] } as Exercise;

            const result = getExerciseCompetencies(exercise);

            expect(result).toEqual([]);
        });

        it('should extract competencies from competencyLinks', () => {
            const competency1 = { id: 1, title: 'Competency 1' } as CourseCompetency;
            const competency2 = { id: 2, title: 'Competency 2' } as CourseCompetency;
            const exercise = {
                id: 1,
                competencyLinks: [{ competency: competency1 } as CompetencyExerciseLink, { competency: competency2 } as CompetencyExerciseLink],
            } as Exercise;

            const result = getExerciseCompetencies(exercise);

            expect(result).toHaveLength(2);
            expect(result[0]).toEqual(competency1);
            expect(result[1]).toEqual(competency2);
        });

        it('should filter out undefined competencies', () => {
            const competency1 = { id: 1, title: 'Competency 1' } as CourseCompetency;
            const exercise = {
                id: 1,
                competencyLinks: [
                    { competency: competency1 } as CompetencyExerciseLink,
                    { competency: undefined } as CompetencyExerciseLink,
                    { competency: competency1 } as CompetencyExerciseLink,
                ],
            } as Exercise;

            const result = getExerciseCompetencies(exercise);

            expect(result).toHaveLength(2);
            expect(result[0]).toEqual(competency1);
            expect(result[1]).toEqual(competency1);
        });

        it('should return empty array when all competencies are undefined', () => {
            const exercise = {
                id: 1,
                competencyLinks: [{ competency: undefined } as CompetencyExerciseLink, { competency: undefined } as CompetencyExerciseLink],
            } as Exercise;

            const result = getExerciseCompetencies(exercise);

            expect(result).toEqual([]);
        });
    });
});
