import { Exercise, ExerciseType } from '../exercise';
import { Course } from '../course';
import { DiagramType } from '@ls1intum/apollon';

export class ModelingExercise extends Exercise {
    public diagramType: DiagramType;
    public sampleSolutionModel: string;
    public sampleSolutionExplanation: string;

    // helper attributs

    public automaticAssessmentSupported = false; // TODO: in the future, we will set this value to true when loading the modeling exercise
    // from the server when the corresponding diagram type supports automatic assessment

    constructor(diagramType: DiagramType, course?: Course) {
        super(ExerciseType.MODELING);
        this.course = course;
        this.diagramType = diagramType;
    }
}

export { DiagramType };
