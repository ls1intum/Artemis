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

/**
 * Normalizes a single category entry from various input formats into an ExerciseCategory.
 * Handles JSON-encoded strings, plain text strings, ExerciseCategory instances, and plain objects with {category, color}.
 */
export const normalizeCategoryEntry = (value: unknown): ExerciseCategory | undefined => {
    if (!value) {
        return undefined;
    }
    if (value instanceof ExerciseCategory) {
        const category = value.category?.trim();
        return category ? new ExerciseCategory(category, value.color) : undefined;
    }
    if (typeof value === 'string') {
        const trimmed = value.trim();
        if (!trimmed) {
            return undefined;
        }
        try {
            const parsed = JSON.parse(trimmed);
            if (parsed && typeof parsed === 'object' && 'category' in parsed) {
                const category = (parsed as { category?: string }).category?.trim();
                if (!category) {
                    return undefined;
                }
                return new ExerciseCategory(category, (parsed as { color?: string }).color);
            }
        } catch {
            // not JSON-encoded, treat as plain text
        }
        return new ExerciseCategory(trimmed, undefined);
    }
    if (typeof value === 'object' && 'category' in value) {
        const category = (value as { category?: string }).category?.trim();
        if (!category) {
            return undefined;
        }
        return new ExerciseCategory(category, (value as { color?: string }).color);
    }
    return undefined;
};

/**
 * Normalizes an array of category entries into ExerciseCategory instances.
 */
export const normalizeCategoryArray = (values: unknown[]): ExerciseCategory[] => {
    return values.map(normalizeCategoryEntry).filter((entry): entry is ExerciseCategory => entry !== undefined);
};

export const toExerciseCategories = (categories?: string[]): ExerciseCategory[] | undefined => {
    if (!categories || categories.length === 0) {
        return undefined;
    }
    const parsed = normalizeCategoryArray(categories);
    return parsed.length > 0 ? parsed : undefined;
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
