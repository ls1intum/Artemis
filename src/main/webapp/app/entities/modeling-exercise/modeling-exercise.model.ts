import { Exercise, ExerciseType } from '../exercise';
import { Course } from '../course';

export const enum DiagramType {
    CLASS = 'CLASS',
    ACTIVITY = 'ACTIVITY',
    USE_CASE = 'USE_CASE',
    COMMUNICATION = 'COMMUNICATION'
}

export class ModelingExercise extends Exercise {

    public diagramType: DiagramType;
    public sampleSolutionModel: String;
    public sampleSolutionExplanation: String;

    constructor(course?: Course, diagramType?: DiagramType) {
        super(ExerciseType.MODELING);
        this.course = course;
        this.diagramType = diagramType;
    }
}
