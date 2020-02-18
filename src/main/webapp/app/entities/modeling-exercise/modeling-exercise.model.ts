import { UMLDiagramType } from '@ls1intum/apollon';
import { Exercise, ExerciseType } from 'app/entities/exercise/exercise.model';
import { Course } from 'app/entities/course/course.model';

export class ModelingExercise extends Exercise {
    public diagramType: UMLDiagramType;
    public sampleSolutionModel: string;
    public sampleSolutionExplanation: string;

    // helper attributs

    public automaticAssessmentSupported = false; // TODO: in the future, we will set this value to true when loading the modeling exercise
    // from the server when the corresponding diagram type supports automatic assessment

    constructor(diagramType: UMLDiagramType, course?: Course) {
        super(ExerciseType.MODELING);
        this.course = course || null;
        this.diagramType = diagramType;
    }
}

export { UMLDiagramType as DiagramType };
