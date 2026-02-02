import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { TeamAssignmentConfig } from 'app/exercise/shared/entities/team/team-assignment-config.model';
import { TeamAssignmentConfigSnapshot } from 'app/exercise/synchronization/exercise-metadata-snapshot.dto';

export const toTeamAssignmentConfig = (snapshot?: TeamAssignmentConfigSnapshot): TeamAssignmentConfig | undefined => {
    if (!snapshot) {
        return undefined;
    }
    const config = new TeamAssignmentConfig();
    config.id = snapshot.id;
    config.minTeamSize = snapshot.minTeamSize;
    config.maxTeamSize = snapshot.maxTeamSize;
    return config;
};

export const toExerciseCategories = (categories?: string[]): ExerciseCategory[] | undefined => {
    if (!categories || categories.length === 0) {
        return undefined;
    }
    return categories.map((category) => new ExerciseCategory(category, undefined));
};
