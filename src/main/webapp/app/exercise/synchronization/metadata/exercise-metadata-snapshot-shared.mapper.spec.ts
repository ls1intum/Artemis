import { describe, expect, it } from 'vitest';
import { Competency, CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { toCompetencyLinks, toGradingCriteria, toTeamAssignmentConfig } from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot-shared.mapper';

import { CompetencyExerciseLinkSnapshotDTO, TeamAssignmentConfigSnapshot } from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot.dto';

describe('ExerciseMetadataSnapshotSharedMapper', () => {
    it('returns undefined when team assignment config snapshot is missing', () => {
        expect(toTeamAssignmentConfig(undefined)).toBeUndefined();
    });

    it('maps team assignment config snapshots', () => {
        const snapshot: TeamAssignmentConfigSnapshot = {
            id: 9,
            minTeamSize: 2,
            maxTeamSize: 5,
        };

        const config = toTeamAssignmentConfig(snapshot);

        expect(config?.id).toBe(9);
        expect(config?.minTeamSize).toBe(2);
        expect(config?.maxTeamSize).toBe(5);
    });

    it('returns undefined when competency links snapshot is missing', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);

        expect(toCompetencyLinks(exercise, undefined)).toBeUndefined();
    });

    it('maps competency links using course competencies and defaults weight', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        const competency = new Competency();
        competency.id = 11;
        competency.title = 'Comp 11';
        exercise.course = { competencies: [competency], prerequisites: [] } as any;

        const snapshot: CompetencyExerciseLinkSnapshotDTO[] = [{ competencyId: { competencyId: 11 } }];
        const mapped = toCompetencyLinks(exercise, snapshot);

        expect(mapped).toHaveLength(1);
        expect(mapped?.[0].competency?.id).toBe(11);
        expect(mapped?.[0].weight).toBe(0.5);
    });

    it('maps competency links using existing link/prerequisite lookups and filters unresolved ids', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);

        const existingCompetency = new Competency();
        existingCompetency.id = 21;
        existingCompetency.title = 'Existing';
        exercise.competencyLinks = [new CompetencyExerciseLink(existingCompetency, exercise, 1)];

        const prerequisite = new Competency();
        prerequisite.id = 22;
        prerequisite.title = 'Prerequisite';
        exercise.course = { competencies: [], prerequisites: [prerequisite] } as any;

        const snapshot: CompetencyExerciseLinkSnapshotDTO[] = [
            { competencyId: { competencyId: 21 }, weight: 0.25 },
            { competencyId: { competencyId: 22 }, weight: 1 },
            { competencyId: { competencyId: 999 }, weight: 1 },
            { competencyId: undefined, weight: 1 },
        ];

        const mapped = toCompetencyLinks(exercise, snapshot);

        expect(mapped).toHaveLength(2);
        expect(mapped?.[0].competency?.id).toBe(21);
        expect(mapped?.[0].weight).toBe(0.25);
        expect(mapped?.[1].competency?.id).toBe(22);
        expect(mapped?.[1].weight).toBe(1);
    });

    it('returns empty array when no snapshot competency links can be resolved', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        const snapshot: CompetencyExerciseLinkSnapshotDTO[] = [{ competencyId: { competencyId: 77 } }];

        expect(toCompetencyLinks(exercise, snapshot)).toEqual([]);
    });

    it('returns empty array for empty snapshot links (intentional clear)', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.competencyLinks = [new CompetencyExerciseLink(new Competency(), exercise, 1)];

        expect(toCompetencyLinks(exercise, [])).toEqual([]);
    });

    it('ignores competencies with undefined ids in existing links and course mappings', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);

        const undefinedIdCompetency = new Competency();
        undefinedIdCompetency.title = 'No Id';
        exercise.competencyLinks = [new CompetencyExerciseLink(undefinedIdCompetency, exercise, 1)];
        exercise.course = { competencies: [undefinedIdCompetency], prerequisites: [] } as any;

        const snapshot: CompetencyExerciseLinkSnapshotDTO[] = [{ competencyId: { competencyId: 1 }, weight: 1 }];
        const mapped = toCompetencyLinks(exercise, snapshot);

        expect(mapped).toEqual([]);
    });

    it('returns undefined when grading criteria snapshot is undefined', () => {
        expect(toGradingCriteria(undefined)).toBeUndefined();
    });

    it('converts plain grading criteria DTOs to GradingCriterion class instances', () => {
        const dtos = [
            {
                id: 1,
                title: 'Criterion 1',
                structuredGradingInstructions: [{ id: 10, credits: 1.5, gradingScale: 'Good', instructionDescription: 'Well done', feedback: 'Great work', usageCount: 0 }],
            },
        ];

        const result = toGradingCriteria(dtos);

        expect(result).toHaveLength(1);
        expect(result![0]).toBeInstanceOf(GradingCriterion);
        expect(result![0].id).toBe(1);
        expect(result![0].title).toBe('Criterion 1');
        expect(result![0].structuredGradingInstructions).toHaveLength(1);
        expect(result![0].structuredGradingInstructions[0]).toBeInstanceOf(GradingInstruction);
        expect(result![0].structuredGradingInstructions[0].credits).toBe(1.5);
        expect(result![0].structuredGradingInstructions[0].gradingScale).toBe('Good');
        expect(result![0].structuredGradingInstructions[0].instructionDescription).toBe('Well done');
        expect(result![0].structuredGradingInstructions[0].feedback).toBe('Great work');
        expect(result![0].structuredGradingInstructions[0].usageCount).toBe(0);
    });

    it('handles grading criteria with empty instructions array', () => {
        const dtos = [{ id: 2, title: 'Empty', structuredGradingInstructions: [] }];

        const result = toGradingCriteria(dtos);

        expect(result).toHaveLength(1);
        expect(result![0].structuredGradingInstructions).toEqual([]);
    });

    it('handles grading criteria with missing instructions property', () => {
        const dtos = [{ id: 3, title: 'No Instructions' }];

        const result = toGradingCriteria(dtos);

        expect(result).toHaveLength(1);
        expect(result![0]).toBeInstanceOf(GradingCriterion);
        expect(result![0].structuredGradingInstructions).toEqual([]);
    });

    it('sorts grading criteria by ID for deterministic order', () => {
        const dtos = [
            { id: 3, title: 'Third' },
            { id: 1, title: 'First' },
            { id: 2, title: 'Second' },
        ];

        const result = toGradingCriteria(dtos);

        expect(result).toHaveLength(3);
        expect(result!.map((c) => c.id)).toEqual([1, 2, 3]);
        expect(result!.map((c) => c.title)).toEqual(['First', 'Second', 'Third']);
    });

    it('defaults missing grading criteria fields to safe values', () => {
        const dtos = [{ structuredGradingInstructions: [{}] }];

        const result = toGradingCriteria(dtos);

        expect(result).toHaveLength(1);
        expect(result![0].title).toBe('');
        expect(result![0].id).toBeUndefined();
        const instruction = result![0].structuredGradingInstructions[0];
        expect(instruction.credits).toBe(0);
        expect(instruction.gradingScale).toBe('');
        expect(instruction.instructionDescription).toBe('');
        expect(instruction.feedback).toBe('');
    });
});
