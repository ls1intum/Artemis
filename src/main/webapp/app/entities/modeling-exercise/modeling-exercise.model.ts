import { Exercise, ExerciseType } from '../exercise';
import { Course } from '../course';

export const enum DiagramType {
    'CLASS',
    'ACTIVITY',
    'USE_CASE',
    'COMMUNICATION'
}

export class ModelingExercise extends Exercise {

    public diagramType: DiagramType;
    public sampleSolutionModel: String;
    public sampleSolutionExplanation: String;

    constructor(course?: Course) {
        super(ExerciseType.MODELING);
        this.course = course;
    }
}
