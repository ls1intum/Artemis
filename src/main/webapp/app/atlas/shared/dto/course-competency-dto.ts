import { CompetencyTaxonomy, CourseCompetency } from 'app/atlas/shared/entities/competency.model';

export interface CourseCompetencyDTO {
    id: number;
    title: string;
    description?: string;
    taxonomy?: CompetencyTaxonomy;
}

/**
 * Maps a CourseCompetency → CourseCompetencyDTO.
 */
export const toCourseCompetencyDTO = (competency: CourseCompetency): CourseCompetencyDTO => {
    if (competency.id === undefined) {
        throw new Error('Cannot map CourseCompetency: id is missing.');
    }
    if (!competency.title) {
        throw new Error('Cannot map CourseCompetency: title is missing.');
    }

    return {
        id: competency.id,
        title: competency.title,
        description: competency.description,
        taxonomy: competency.taxonomy,
    };
};
