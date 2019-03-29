import { Exercise, ExerciseType } from '../exercise';
import { Course } from '../course';
import { DiagramType } from '@ls1intum/apollon';

export class ModelingExercise extends Exercise {
    public diagramType: DiagramType;
    public sampleSolutionModel: String;
    public sampleSolutionExplanation: String;

    constructor(diagramType: DiagramType, course?: Course) {
        super(ExerciseType.MODELING);
        this.course = course;
        this.diagramType = diagramType;
    }
}

export { DiagramType };
