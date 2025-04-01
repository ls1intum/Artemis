import { CourseCompetency, CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';

export class Prerequisite extends CourseCompetency {
    constructor() {
        super(CourseCompetencyType.PREREQUISITE);
    }
}
