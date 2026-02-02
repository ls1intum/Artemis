import { describe, expect, it } from 'vitest';

import { toExerciseCategories, toTeamAssignmentConfig } from 'app/exercise/synchronization/exercise-metadata-snapshot-shared.mapper';

import { TeamAssignmentConfigSnapshot } from 'app/exercise/synchronization/exercise-metadata-snapshot.dto';

describe('ExerciseMetadataSnapshotSharedMapper', () => {
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

    it('maps exercise categories to entities', () => {
        const categories = toExerciseCategories(['alpha', 'beta']);

        expect(categories?.length).toBe(2);
        expect(categories?.[0].category).toBe('alpha');
        expect(categories?.[1].category).toBe('beta');
    });
});
