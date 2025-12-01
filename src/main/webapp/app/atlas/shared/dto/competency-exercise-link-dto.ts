import { CourseCompetencyDTO, toCourseCompetencyDTO } from 'app/atlas/shared/dto/course-competency-dto';
import { CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';

export interface CompetencyExerciseLinkDTO {
    courseCompetencyDTO: CourseCompetencyDTO;
    weight: number;
    courseId?: number;
}

/**
 * Maps a CompetencyExerciseLink client model → CompetencyExerciseLinkDTO for API updates.
 */
export function toCompetencyExerciseLinkDTO(link: CompetencyExerciseLink): CompetencyExerciseLinkDTO {
    if (!link.competency) {
        throw new Error('Cannot map CompetencyExerciseLink: missing competency.');
    }

    return {
        courseCompetencyDTO: toCourseCompetencyDTO(link.competency),
        weight: link.weight,
        courseId: link.competency.course?.id ?? undefined,
    };
}

/**
 * Maps an array of CompetencyExerciseLink to DTO list.
 */
export function mapCompetencyLinks(links: CompetencyExerciseLink[] | undefined): CompetencyExerciseLinkDTO[] {
    return links?.map((l) => toCompetencyExerciseLinkDTO(l)) ?? [];
}
