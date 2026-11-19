import { PresentationRubricScale } from 'app/presentation/shared/entities/presentation-assessment.model';

/** The fixed keys of the levels of each scale, from the lowest to the highest level. */
const LEVEL_KEYS: Record<PresentationRubricScale, readonly string[]> = {
    [PresentationRubricScale.THREE_POINT]: ['poor', 'satisfactory', 'excellent'],
    [PresentationRubricScale.FIVE_POINT]: ['poor', 'needsImprovement', 'satisfactory', 'good', 'excellent'],
};

/** The most criteria a rubric can have. Keep in sync with MAX_RUBRIC_CRITERIA in Constants.java. */
export const MAX_RUBRIC_CRITERIA = 20;
export const RUBRIC_WEIGHT_SUM = 100;
/** The longest name of a rubric criterion. Keep in sync with PresentationRubricCriterion.MAX_NAME_LENGTH. */
export const MAX_NAME_LENGTH = 255;
/** The longest description of a rubric criterion or level. Keep in sync with PresentationRubricCriterion.MAX_DESCRIPTION_LENGTH. */
export const MAX_DESCRIPTION_LENGTH = 1000;
/** The longest comment of a rubric rating. Keep in sync with PresentationRubricRating.MAX_COMMENT_LENGTH. */
export const MAX_COMMENT_LENGTH = 1000;

export function rubricLevelCount(scale: PresentationRubricScale): number {
    return LEVEL_KEYS[scale].length;
}

/** The translation key of the level at the given position, which counts from 0 at the lowest level. */
export function rubricLevelKey(scale: PresentationRubricScale, position: number): string {
    return `artemisApp.presentationAssessment.rubric.levelKey.${LEVEL_KEYS[scale][position]}`;
}
