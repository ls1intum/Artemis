import { Result } from 'app/exercise/shared/entities/result/result.model';
import { UMLModel, getAssessmentNameForArtemis } from '@tumaet/apollon';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { getModelElementIds } from '../../shared/apollon-model.util';

export type AssessmentNamesForModelId = { [modelId: string]: { type: string; name: string } | undefined };

/**
 * Creates the labels for the assessment elements for displaying them in the modeling and assessment editor.
 */
// TODO: define a mapping or simplify this complex monster in a another way so that we can support other diagram types as well
export function getNamesForAssessments(result: Result, model: UMLModel): AssessmentNamesForModelId {
    const assessmentsNames: AssessmentNamesForModelId = {};

    for (const feedback of result.feedbacks!) {
        const referencedModelId = feedback.referenceId!;
        if (referencedModelId) {
            assessmentsNames[referencedModelId] = getAssessmentNameForArtemis(referencedModelId, model);
        } else {
            assessmentsNames[referencedModelId] = { name: '', type: '' };
        }
    }

    return assessmentsNames;
}

/**
 * Removes feedback elements for which the corresponding model element does not exist in the model anymore.
 * @param feedbacks the list of feedback to filter
 * @param umlModel the UML model containing the references
 */
export function filterInvalidFeedback(feedbacks: Feedback[], umlModel: UMLModel | undefined): Feedback[] {
    if (!feedbacks) {
        return feedbacks;
    }
    if (!umlModel) {
        return [];
    }

    const availableIds = getModelElementIds(umlModel);
    return feedbacks.filter((feedback) => feedback.referenceId && availableIds.has(feedback.referenceId));
}
