import { CompetencyExerciseLink, CourseCompetency, MEDIUM_COMPETENCY_LINK_WEIGHT } from 'app/atlas/shared/entities/competency.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { TeamAssignmentConfig } from 'app/exercise/shared/entities/team/team-assignment-config.model';
import {
    CompetencyExerciseLinkSnapshotDTO,
    GradingCriterionSnapshotDTO,
    GradingInstructionSnapshotDTO,
    TeamAssignmentConfigSnapshot,
} from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot.dto';

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
            // Competency not found locally — this can happen if another instructor added
            // a competency that is not yet in the current editor's course data. The link
            // is intentionally dropped from the resolved result; a page refresh will
            // resolve it with the latest course competencies. Note: when the user accepts
            // "incoming" during conflict resolution, this dropped link will be missing
            // from the applied value — this is an accepted limitation of the current
            // approach and does not affect the persisted exercise state.
            continue;
        }
        mapped.push(new CompetencyExerciseLink(competency, exercise, link.weight ?? MEDIUM_COMPETENCY_LINK_WEIGHT));
    }
    return mapped;
};

/**
 * Converts a grading instruction snapshot DTO (plain JSON) into a GradingInstruction class instance.
 */
const toGradingInstruction = (dto: GradingInstructionSnapshotDTO): GradingInstruction => {
    const instruction = new GradingInstruction();
    instruction.id = dto.id;
    instruction.credits = dto.credits ?? 0;
    instruction.gradingScale = dto.gradingScale ?? '';
    instruction.instructionDescription = dto.instructionDescription ?? '';
    instruction.feedback = dto.feedback ?? '';
    instruction.usageCount = dto.usageCount;
    return instruction;
};

/**
 * Converts a grading criterion snapshot DTO (plain JSON) into a GradingCriterion class instance
 * with nested GradingInstruction instances.
 */
const toGradingCriterionEntry = (dto: GradingCriterionSnapshotDTO): GradingCriterion => {
    const criterion = new GradingCriterion();
    criterion.id = dto.id;
    criterion.title = dto.title ?? '';
    criterion.structuredGradingInstructions = (dto.structuredGradingInstructions ?? []).map(toGradingInstruction);
    return criterion;
};

/**
 * Converts grading criteria from snapshot DTO format to proper GradingCriterion class instances.
 * The server sends {@code Set<GradingCriterionDTO>} which arrives as plain JSON objects after
 * deserialization; this mapper creates proper class instances expected by the exercise model.
 */
export const toGradingCriteria = (snapshotCriteria: GradingCriterionSnapshotDTO[] | undefined): GradingCriterion[] | undefined => {
    if (!snapshotCriteria) {
        return undefined;
    }
    // Sort by ID for a deterministic order — the server collects criteria into a Set
    // (unordered), so without sorting the incoming side can appear in a different order
    // than the user's local criteria in the conflict modal.
    return snapshotCriteria.map(toGradingCriterionEntry).sort((a, b) => (a.id ?? -1) - (b.id ?? -1));
};
