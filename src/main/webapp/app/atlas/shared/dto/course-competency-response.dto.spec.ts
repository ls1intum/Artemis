import { describe, expect, it } from 'vitest';
import { CompetencyExerciseLink, CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { toCompetency, toCompetencyRelationDTO } from 'app/atlas/shared/dto/course-competency-response.dto';
import { toCompetencyExerciseLinkDTO } from 'app/atlas/shared/dto/competency-exercise-link-dto';
import { CourseCompetencyResponse, CourseCompetencyResponseTaxonomyEnumValues, CourseCompetencyResponseTypeEnumValues } from 'app/openapi/model/course-competency-response';
import { CompetencyProgressConfidenceReasonEnumValues } from 'app/openapi/model/competency-progress';
import { CompetencyRelationRelationTypeEnumValues } from 'app/openapi/model/competency-relation';
import { AttachmentForCompetencyAttachmentTypeEnumValues } from 'app/openapi/model/attachment-for-competency';
import {
    ExerciseForCompetency,
    ExerciseForCompetencyAssessmentTypeEnumValues,
    ExerciseForCompetencyDifficultyEnumValues,
    ExerciseForCompetencyIncludedInOverallScoreEnumValues,
    ExerciseForCompetencyModeEnumValues,
    ExerciseForCompetencyTypeEnumValues,
} from 'app/openapi/model/exercise-for-competency';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';

describe('course competency response mapping', () => {
    const wireCompetency: CourseCompetencyResponse = {
        id: 1,
        masteryThreshold: 100,
        optional: false,
        generatedByAi: false,
        type: 'competency',
    };

    function hydrateExercise(exercise: ExerciseForCompetency) {
        return toCompetency({ ...wireCompetency, exerciseLinks: [{ weight: 1, generatedByAi: false, exercise }] }).exerciseLinks?.[0].exercise;
    }

    it('maps competency and learning-object provenance from server responses', () => {
        const competency = toCompetency({
            ...wireCompetency,
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

    it('preserves manual provenance and omits it from update payloads', () => {
        const competency = toCompetency({ ...wireCompetency, title: 'Sorting' });

        expect(competency.generatedByAi).toBe(false);

        const updateLink = new CompetencyExerciseLink(competency, undefined, 1.0, true);
        expect(toCompetencyExerciseLinkDTO(updateLink)).not.toHaveProperty('generatedByAi');
    });

    it('keeps optional fields absent when omitted by the server', () => {
        const competency = toCompetency(wireCompetency);
        expect(competency.title).toBeUndefined();
        expect(competency.taxonomy).toBeUndefined();
        expect(competency.softDueDate).toBeUndefined();
        expect(competency.userProgress).toBeUndefined();
        expect(competency.course).toBeUndefined();
        expect(competency.exerciseLinks).toBeUndefined();
        expect(competency.lectureUnitLinks).toBeUndefined();
    });

    it.each(CourseCompetencyResponseTaxonomyEnumValues)('maps taxonomy %s', (taxonomy) => {
        expect(toCompetency({ ...wireCompetency, taxonomy }).taxonomy).toBe(taxonomy);
    });

    it.each(CourseCompetencyResponseTypeEnumValues)('maps course competency type %s', (type) => {
        expect(toCompetency({ ...wireCompetency, type }).type).toBe(type);
    });

    it.each(CompetencyProgressConfidenceReasonEnumValues)('maps confidence reason %s', (confidenceReason) => {
        expect(toCompetency({ ...wireCompetency, userProgress: [{ confidenceReason }] }).userProgress?.[0].confidenceReason).toBe(confidenceReason);
    });

    it.each(CompetencyRelationRelationTypeEnumValues)('maps relation type %s', (relationType) => {
        const relation = { id: 2, tailCompetencyId: 3, headCompetencyId: 4, relationType };
        expect(toCompetencyRelationDTO(relation)).toEqual(relation);
    });

    it.each(ExerciseForCompetencyTypeEnumValues)('maps exercise type %s', (type) => {
        expect(hydrateExercise({ type })?.type).toBe(type);
    });

    it.each(ExerciseForCompetencyAssessmentTypeEnumValues)('maps assessment type %s', (assessmentType) => {
        expect(hydrateExercise({ type: 'text', assessmentType })?.assessmentType).toBe(assessmentType);
    });

    it.each(ExerciseForCompetencyDifficultyEnumValues)('maps difficulty %s', (difficulty) => {
        expect(hydrateExercise({ type: 'text', difficulty })?.difficulty).toBe(difficulty);
    });

    it.each(ExerciseForCompetencyIncludedInOverallScoreEnumValues)('maps overall-score inclusion %s', (includedInOverallScore) => {
        expect(hydrateExercise({ type: 'text', includedInOverallScore })?.includedInOverallScore).toBe(includedInOverallScore);
    });

    it.each(ExerciseForCompetencyModeEnumValues)('maps exercise mode %s', (mode) => {
        const exercise = hydrateExercise({ type: 'text', mode });
        expect(exercise?.mode).toBe(mode);
        expect(exercise?.teamMode).toBe(mode === 'TEAM');
    });

    it('hydrates serialized exercise categories and skips malformed entries', () => {
        const exercise = hydrateExercise({ type: 'text', categories: ['{"category":"Sorting","color":"blue"}', 'invalid'] });

        expect(exercise?.categories).toEqual([new ExerciseCategory('Sorting', 'blue')]);
        expect(exercise?.categories?.[0]).toBeInstanceOf(ExerciseCategory);
    });

    it.each(AttachmentForCompetencyAttachmentTypeEnumValues)('maps attachment type %s', (attachmentType) => {
        const competency = toCompetency({
            ...wireCompetency,
            lectureUnitLinks: [{ weight: 1, generatedByAi: false, lectureUnit: { type: 'attachment', attachment: { attachmentType } } }],
        });

        const lectureUnit = competency.lectureUnitLinks?.[0].lectureUnit;
        expect(lectureUnit).toBeInstanceOf(AttachmentVideoUnit);
        expect(lectureUnit).toMatchObject({ attachment: { attachmentType } });
    });

    it('uses a text unit for an unknown lecture unit type', () => {
        const competency = toCompetency({
            ...wireCompetency,
            lectureUnitLinks: [{ weight: 1, generatedByAi: false, lectureUnit: { id: 2, type: 'future-unit' } }],
        });

        const lectureUnit = competency.lectureUnitLinks?.[0].lectureUnit;
        expect(lectureUnit).toBeInstanceOf(TextUnit);
        expect(lectureUnit?.id).toBe(2);
    });
});
