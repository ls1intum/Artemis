import { PresentationRubricCriterion, PresentationRubricScale } from 'app/presentation/shared/entities/presentation-assessment.model';
import { rubricLevelCount } from 'app/presentation/shared/presentation-rubric.util';

/**
 * A rubric criterion with one level per position of its scale, the lowest first.
 * With an id, the criterion and its levels get ids, the levels counting from id * 10 + 1; with texts, the criterion and its levels get descriptions.
 */
export function rubricCriterion(
    name: string,
    weight: number,
    scale = PresentationRubricScale.FIVE_POINT,
    { id, texts = false }: { id?: number; texts?: boolean } = {},
): PresentationRubricCriterion {
    return {
        id,
        name,
        description: texts ? `${name} description` : undefined,
        weight,
        scale,
        levels: Array.from({ length: rubricLevelCount(scale) }, (_, position) => ({
            id: id === undefined ? undefined : id * 10 + position + 1,
            description: texts ? `${name} level ${position + 1}` : undefined,
        })),
    };
}
