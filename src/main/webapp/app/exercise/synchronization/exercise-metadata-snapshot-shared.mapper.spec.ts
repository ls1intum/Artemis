import { describe, expect, it } from 'vitest';
import { Competency, CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { toCompetencyLinks, toTeamAssignmentConfig } from 'app/exercise/synchronization/exercise-metadata-snapshot-shared.mapper';

import { CompetencyExerciseLinkSnapshotDTO, TeamAssignmentConfigSnapshot } from 'app/exercise/synchronization/exercise-metadata-snapshot.dto';

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

    it('returns undefined when no snapshot competency links can be resolved', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        const snapshot: CompetencyExerciseLinkSnapshotDTO[] = [{ competencyId: { competencyId: 77 } }];

        expect(toCompetencyLinks(exercise, snapshot)).toBeUndefined();
    });

    it('ignores competencies with undefined ids in existing links and course mappings', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);

        const undefinedIdCompetency = new Competency();
        undefinedIdCompetency.title = 'No Id';
        exercise.competencyLinks = [new CompetencyExerciseLink(undefinedIdCompetency, exercise, 1)];
        exercise.course = { competencies: [undefinedIdCompetency], prerequisites: [] } as any;

        const snapshot: CompetencyExerciseLinkSnapshotDTO[] = [{ competencyId: { competencyId: 1 }, weight: 1 }];
        const mapped = toCompetencyLinks(exercise, snapshot);

        expect(mapped).toBeUndefined();
    });
});
