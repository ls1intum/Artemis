import { Result } from 'app/entities/result.model';
import { UMLElementType, UMLModelCompat, UMLRelationshipType, findElement, findRelationship } from '@ls1intum/apollon';
import { Feedback } from 'app/entities/feedback.model';

export type AssessmentNamesForModelId = { [modelId: string]: { type: string; name: string } | undefined };

/**
 * Creates the labels for the assessment elements for displaying them in the modeling and assessment editor.
 */
// TODO: define a mapping or simplify this complex monster in a another way so that we can support other diagram types as well
export function getNamesForAssessments(result: Result, model: UMLModelCompat): AssessmentNamesForModelId {
    const assessmentsNames: AssessmentNamesForModelId = {};
    for (const feedback of result.feedbacks!) {
        const referencedModelType = feedback.referenceType! as UMLElementType;
        const referencedModelId = feedback.referenceId!;
        if (referencedModelType in UMLElementType) {
            const element = findElement(model, referencedModelId);
            if (!element) {
                // prevent errors when element could not be found, should never happen
                assessmentsNames[referencedModelId] = { name: '', type: '' };
                continue;
            }

            const name = element.name;
            let type: string;
            switch (element.type) {
                case UMLElementType.Class:
                    type = 'class';
                    break;
                case UMLElementType.Package:
                    type = 'package';
                    break;
                case UMLElementType.Interface:
                    type = 'interface';
                    break;
                case UMLElementType.AbstractClass:
                    type = 'abstract class';
                    break;
                case UMLElementType.Enumeration:
                    type = 'enum';
                    break;
                case UMLElementType.ClassAttribute:
                    type = 'attribute';
                    break;
                case UMLElementType.ClassMethod:
                    type = 'method';
                    break;
                case UMLElementType.ActivityInitialNode:
                    type = 'initial node';
                    break;
                case UMLElementType.ActivityFinalNode:
                    type = 'final node';
                    break;
                case UMLElementType.ActivityObjectNode:
                    type = 'object';
                    break;
                case UMLElementType.ActivityActionNode:
                    type = 'action';
                    break;
                case UMLElementType.ActivityForkNode:
                    type = 'fork node';
                    break;
                case UMLElementType.ActivityMergeNode:
                    type = 'merge node';
                    break;
                default:
                    type = '';
                    break;
            }
            assessmentsNames[referencedModelId] = { type, name };
        } else if (referencedModelType in UMLRelationshipType) {
            const relationship = findRelationship(model, referencedModelId);
            if (!relationship) {
                // prevent errors when relationship could not be found, should never happen
                assessmentsNames[referencedModelId] = { name: '', type: '' };
                continue;
            }
            const source = findElement(model, relationship.source.element)?.name ?? '?';
            const target = findElement(model, relationship.target.element)?.name ?? '?';
            const relationshipType = relationship.type;
            let type = 'association';
            let relation: string;
            switch (relationshipType) {
                case UMLRelationshipType.ClassBidirectional:
                    relation = ' <-> ';
                    break;
                case UMLRelationshipType.ClassUnidirectional:
                    relation = ' --> ';
                    break;
                case UMLRelationshipType.ClassAggregation:
                    relation = ' --◇ ';
                    break;
                case UMLRelationshipType.ClassInheritance:
                    relation = ' --▷ ';
                    break;
                case UMLRelationshipType.ClassDependency:
                    relation = ' ╌╌> ';
                    break;
                case UMLRelationshipType.ClassComposition:
                    relation = ' --◆ ';
                    break;
                case UMLRelationshipType.ActivityControlFlow:
                    relation = ' --> ';
                    type = 'control flow';
                    break;
                default:
                    relation = ' --- ';
            }
            assessmentsNames[referencedModelId] = { type, name: source + relation + target };
        } else {
            assessmentsNames[referencedModelId] = { type: `${referencedModelType}`, name: '' };
        }
    }
    return assessmentsNames;
}

/**
 * Removes feedback elements for which the corresponding model element does not exist in the model anymore.
 * @param feedbacks the list of feedback to filter
 * @param umlModel the UML model containing the references
 */
export function filterInvalidFeedback(feedbacks: Feedback[], umlModel: UMLModelCompat): Feedback[] {
    if (!feedbacks) {
        return feedbacks;
    }
    if (!umlModel || !umlModel.elements) {
        return [];
    }

    let availableIds: string[] = Object.values(umlModel.elements).map((el) => el.id);
    if (umlModel.relationships) {
        availableIds = availableIds.concat(Object.values(umlModel.relationships).map((rel) => rel.id));
    }
    return feedbacks.filter((feedback) => availableIds.includes(feedback.referenceId!));
}
