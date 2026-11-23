import { IconDefinition, faCheck, faHashtag, faTableCellsLarge } from '@fortawesome/free-solid-svg-icons';

import { PresentationGradingMethod } from 'app/presentation/shared/entities/presentation-assessment.model';

/** The icon of each grading method, shared by the method cards of the create dialog and the method badge. */
export const GRADING_METHOD_ICONS: Record<PresentationGradingMethod, IconDefinition> = {
    [PresentationGradingMethod.PASS_FAIL]: faCheck,
    [PresentationGradingMethod.SCORE]: faHashtag,
    [PresentationGradingMethod.RUBRIC]: faTableCellsLarge,
};
