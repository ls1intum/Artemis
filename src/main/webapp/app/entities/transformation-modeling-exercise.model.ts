import { ExerciseType } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';

export class TransformationModelingExercise extends ModelingExercise {
    public diagramType?: UMLDiagramType;
    public problemDiagramType?: UMLDiagramType;
    public problemModel?: string;
    public correctionScheme?: string;

    constructor(problemDiagramType: UMLDiagramType, diagramType: UMLDiagramType, course: Course | undefined, exerciseGroup: ExerciseGroup | undefined) {
        super(diagramType, course, exerciseGroup);
        this.type = ExerciseType.TRANSFORMATION;
        this.problemDiagramType = problemDiagramType;
        this.assessmentType = AssessmentType.AUTOMATIC;
    }
}
