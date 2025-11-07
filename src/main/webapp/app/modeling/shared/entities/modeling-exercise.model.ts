import { DifficultyLevel, Exercise, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { type UMLDiagramType, UMLDiagramType as UMLDiagramTypes } from '@ls1intum/apollon';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';

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
        if (this.diagramType === UMLDiagramTypes.ClassDiagram || this.diagramType === UMLDiagramTypes.ActivityDiagram) {
            this.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        }
    }
}

export interface UpdateModelingExerciseDTO {
    id: number;

    title?: string;
    channelName?: string;
    problemStatement?: string;
    categories?: string[];

    difficulty?: DifficultyLevel;
    maxPoints?: number;
    bonusPoints?: number;
    includedInOverallScore?: IncludedInOverallScore;

    releaseDate?: string;
    startDate?: string;
    dueDate?: string;
    assessmentDueDate?: string;
    exampleSolutionPublicationDate?: string;

    diagramType?: UMLDiagramType;
    exampleSolutionModel?: string;
    exampleSolutionExplanation?: string;

    courseId?: number;
    exerciseGroupId?: number;

    gradingCriteria?: GradingCriterion[];
    competencyLinks?: CompetencyExerciseLink[];
}

export function toUpdateModelingExerciseDTO(modelingExercise: ModelingExercise): UpdateModelingExerciseDTO {
    let copy = ExerciseService.convertExerciseDatesFromClient(modelingExercise);
    copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
    copy.categories = ExerciseService.stringifyExerciseCategories(copy);

    return {
        id: copy.id!,
        title: copy.title,
        channelName: copy.channelName,
        problemStatement: copy.problemStatement,
        categories: (copy.categories ?? []) as unknown as string[],
        difficulty: copy.difficulty,
        maxPoints: copy.maxPoints,
        bonusPoints: copy.bonusPoints,
        includedInOverallScore: copy.includedInOverallScore,
        releaseDate: copy.releaseDate as unknown as string,
        startDate: copy.startDate as unknown as string,
        dueDate: copy.dueDate as unknown as string,
        assessmentDueDate: copy.assessmentDueDate as unknown as string,
        exampleSolutionPublicationDate: copy.exampleSolutionPublicationDate as unknown as string,
        diagramType: copy.diagramType,
        exampleSolutionModel: copy.exampleSolutionModel,
        exampleSolutionExplanation: copy.exampleSolutionExplanation,
        courseId: copy.course?.id,
        exerciseGroupId: copy.exerciseGroup?.id,
        gradingCriteria: copy.gradingCriteria ?? [],
        competencyLinks: copy.competencyLinks ?? [],
    };
}
