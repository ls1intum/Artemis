import { describe, expect, it } from 'vitest';
import { CompetencyExerciseLink, CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { toCompetency } from 'app/atlas/shared/dto/course-competency-response.dto';
import { toCompetencyExerciseLinkDTO } from 'app/atlas/shared/dto/competency-exercise-link-dto';

describe('course competency provenance mapping', () => {
    it('maps competency and learning-object provenance from server responses', () => {
        const competency = toCompetency({
            id: 1,
            title: 'Sorting',
            type: CourseCompetencyType.COMPETENCY,
            generatedByAi: true,
            exerciseLinks: [{ weight: 0.5, generatedByAi: true, exercise: { id: 2, type: ExerciseType.TEXT } }],
            lectureUnitLinks: [{ weight: 1.0, generatedByAi: true, lectureUnit: { id: 3, type: LectureUnitType.TEXT } }],
        });

        expect(competency.generatedByAi).toBe(true);
        expect(competency.exerciseLinks?.[0].generatedByAi).toBe(true);
        expect(competency.lectureUnitLinks?.[0].generatedByAi).toBe(true);
    });

    it('defaults missing provenance to manual and omits it from update payloads', () => {
        const competency = toCompetency({ id: 1, title: 'Sorting', type: CourseCompetencyType.COMPETENCY });

        expect(competency.generatedByAi).toBe(false);

        const updateLink = new CompetencyExerciseLink(competency, undefined, 1.0, true);
        expect(toCompetencyExerciseLinkDTO(updateLink)).not.toHaveProperty('generatedByAi');
    });
});
