import { CompetencyExerciseLink, CourseCompetency, MEDIUM_COMPETENCY_LINK_WEIGHT } from 'app/atlas/shared/entities/competency.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TeamAssignmentConfig } from 'app/exercise/shared/entities/team/team-assignment-config.model';
import { CompetencyExerciseLinkSnapshotDTO, TeamAssignmentConfigSnapshot } from 'app/exercise/synchronization/exercise-metadata-snapshot.dto';

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

/**
 * Maps competency link snapshots onto the current exercise's resolved competencies.
 */
export const toCompetencyLinks = (exercise: Exercise, snapshotLinks: CompetencyExerciseLinkSnapshotDTO[] | undefined): CompetencyExerciseLink[] | undefined => {
    if (!snapshotLinks) {
        return undefined;
    }
    const competencyById = new Map<number, CourseCompetency>();
    const existingLinks = exercise.competencyLinks ?? [];
    for (const link of existingLinks) {
        if (link.competency?.id != undefined) {
            competencyById.set(link.competency.id, link.competency);
        }
    }
    const courseCompetencies = exercise.course?.competencies ?? [];
    const coursePrerequisites = exercise.course?.prerequisites ?? [];
    for (const competency of [...courseCompetencies, ...coursePrerequisites]) {
        if (competency.id != undefined) {
            competencyById.set(competency.id, competency);
        }
    }
    const mapped: CompetencyExerciseLink[] = [];
    for (const link of snapshotLinks) {
        const competencyId = link.competencyId?.competencyId;
        if (competencyId == undefined) {
            continue;
        }
        const competency = competencyById.get(competencyId);
        if (!competency) {
            continue;
        }
        mapped.push(new CompetencyExerciseLink(competency, exercise, link.weight ?? MEDIUM_COMPETENCY_LINK_WEIGHT));
    }
    return mapped.length > 0 ? mapped : undefined;
};
