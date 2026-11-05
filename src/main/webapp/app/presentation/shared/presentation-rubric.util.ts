import { PresentationRubricScale } from 'app/presentation/shared/entities/presentation-assessment.model';

/** The fixed keys of the levels of each scale, from the lowest to the highest level. */
const LEVEL_KEYS: Record<PresentationRubricScale, readonly string[]> = {
    [PresentationRubricScale.THREE_POINT]: ['poor', 'satisfactory', 'excellent'],
    [PresentationRubricScale.FIVE_POINT]: ['poor', 'needsImprovement', 'satisfactory', 'good', 'excellent'],
};

export const MAX_RUBRIC_CRITERIA = 20;
export const RUBRIC_WEIGHT_SUM = 100;

export function rubricLevelCount(scale: PresentationRubricScale): number {
    return LEVEL_KEYS[scale].length;
}

/** The translation key of the level at the given position, which counts from 0 at the lowest level. */
export function rubricLevelKey(scale: PresentationRubricScale, position: number): string {
    return `artemisApp.presentationAssessment.rubric.levelKey.${LEVEL_KEYS[scale][position]}`;
}
