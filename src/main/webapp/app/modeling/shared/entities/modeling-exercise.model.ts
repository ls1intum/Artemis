import { DifficultyLevel, Exercise, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { type UMLDiagramType, UMLDiagramType as UMLDiagramTypes } from '@ls1intum/apollon';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { convertDateFromClient } from 'app/shared/util/date.utils';

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

    exampleSolutionModel?: string;
    exampleSolutionExplanation?: string;

    courseId?: number;
    exerciseGroupId?: number;

    gradingCriteria?: GradingCriterion[];
}

/**
 * Convert ModelingExercise → Update DTO.
 */
export function toUpdateModelingExerciseDTO(modelingExercise: ModelingExercise): UpdateModelingExerciseDTO {
    modelingExercise = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(modelingExercise);
    const categories = ExerciseService.stringifyExerciseDTOCategories(modelingExercise);
    return {
        id: modelingExercise.id!,
        title: modelingExercise.title,
        channelName: modelingExercise.channelName,
        problemStatement: modelingExercise.problemStatement,
        categories: categories,
        difficulty: modelingExercise.difficulty,
        maxPoints: modelingExercise.maxPoints,
        bonusPoints: modelingExercise.bonusPoints,
        includedInOverallScore: modelingExercise.includedInOverallScore,
        releaseDate: convertDateFromClient(modelingExercise.releaseDate),
        startDate: convertDateFromClient(modelingExercise.startDate),
        dueDate: convertDateFromClient(modelingExercise.dueDate),
        assessmentDueDate: convertDateFromClient(modelingExercise.assessmentDueDate),
        exampleSolutionPublicationDate: convertDateFromClient(modelingExercise.exampleSolutionPublicationDate),
        exampleSolutionModel: modelingExercise.exampleSolutionModel,
        exampleSolutionExplanation: modelingExercise.exampleSolutionExplanation,
        courseId: modelingExercise.course?.id,
        exerciseGroupId: modelingExercise.exerciseGroup?.id,
        gradingCriteria: modelingExercise.gradingCriteria ?? [],
    };
}
