import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { UMLDiagramType } from '@tumaet/apollon';

export class ModelingExercise extends Exercise {
    public diagramType?: UMLDiagramType;
    public exampleSolutionModel?: string;
    public exampleSolutionExplanation?: string;

    constructor(diagramType: UMLDiagramType, course: Course | undefined, exerciseGroup: ExerciseGroup | undefined) {
        super(ExerciseType.MODELING);
        this.course = course;
        this.exerciseGroup = exerciseGroup;
        this.diagramType = diagramType;
        // default value

        this.assessmentType = AssessmentType.MANUAL;
        if (this.diagramType === UMLDiagramType.ClassDiagram || this.diagramType === UMLDiagramType.ActivityDiagram) {
            this.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        }
    }
}
