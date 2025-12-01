import { CompetencyTaxonomy, CourseCompetency } from 'app/atlas/shared/entities/competency.model';

export interface CourseCompetencyDTO {
    id: number;
    title: string;
    description?: string;
    taxonomy?: CompetencyTaxonomy;
}

/**
 * Convert CourseCompetency (client model) → CourseCompetencyDTO (API DTO).
 */
export function toCourseCompetencyDTO(competency: CourseCompetency): CourseCompetencyDTO {
    if (competency.id === undefined) {
        throw new Error('Cannot map CourseCompetency to DTO: id is missing.');
    }
    if (!competency.title) {
        throw new Error('Cannot map CourseCompetency to DTO: title is missing.');
    }
    return {
        id: competency.id,
        title: competency.title,
        description: competency.description ?? undefined,
        taxonomy: competency.taxonomy ?? undefined,
    };
}
