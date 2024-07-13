import { CompetencyTaxonomy, CourseCompetency, CourseCompetencyType } from 'app/entities/competency.model';

export class Prerequisite extends CourseCompetency {
    constructor() {
        super(CourseCompetencyType.PREREQUISITE);
    }
}

export interface PrerequisiteResponseDTO extends Omit<Prerequisite, 'course'> {
    linkedCourseCompetencyDTO?: LinkedCourseCompetencyDTO;
}

export interface LinkedCourseCompetencyDTO {
    id: number;
    courseId: number;
    courseTitle: string;
    semester: string;
}

export interface PrerequisiteRequestDTO {
    title?: string;
    description?: string;
    taxonomy?: CompetencyTaxonomy;
    softDueDate?: string;
    masteryThreshold?: number;
    optional?: boolean;
}
