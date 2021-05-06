import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';

/**
 * The UMLDiagramType enumeration. This has to be exactly the same as defined in Apollon (see diagram-type.d.ts)
 */
export enum UMLDiagramType {
    ClassDiagram = 'ClassDiagram',
    ObjectDiagram = 'ObjectDiagram',
    ActivityDiagram = 'ActivityDiagram',
    UseCaseDiagram = 'UseCaseDiagram',
    CommunicationDiagram = 'CommunicationDiagram',
    ComponentDiagram = 'ComponentDiagram',
    DeploymentDiagram = 'DeploymentDiagram',
    PetriNet = 'PetriNet',
    SyntaxTree = 'SyntaxTree',
    Flowchart = 'Flowchart',
}

export class ModelingExercise extends Exercise {
    public diagramType?: UMLDiagramType;
    public sampleSolutionModel?: string;
    public sampleSolutionExplanation?: string;

    constructor(diagramType: UMLDiagramType, course: Course | undefined, exerciseGroup: ExerciseGroup | undefined) {
        super(ExerciseType.MODELING);
        this.course = course;
        this.exerciseGroup = exerciseGroup;
        this.diagramType = diagramType;
        // default value
        // NOTE: at the moment Compass does not work reliably and is therefore deactivated.
        // TODO: Melih Oezbeyli(iozbeyli) Reactivate this code after hazelcast issue is resolved
        this.assessmentType = AssessmentType.MANUAL;
        // if (this.diagramType === UMLDiagramType.ClassDiagram || this.diagramType === UMLDiagramType.ActivityDiagram) {
        //     this.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        // }
    }
}
