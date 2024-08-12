import { CourseCompetency, CourseCompetencyType } from 'app/entities/competency.model';

export class Prerequisite extends CourseCompetency {
    constructor() {
        super(CourseCompetencyType.PREREQUISITE);
    }
}
