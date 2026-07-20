import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { HyperionAssessmentCriteriaGenerationApi } from 'app/openapi/api/hyperion-assessment-criteria-generation-api';
import { AssessmentCriteriaGenerationRequest, AssessmentCriteriaGenerationRequestExerciseTypeEnum } from 'app/openapi/model/assessment-criteria-generation-request';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';

export interface AssessmentCriteriaGenerationCall {
    courseId: number;
    request: AssessmentCriteriaGenerationRequest;
}

@Injectable({ providedIn: 'root' })
export class AssessmentCriteriaGenerationService {
    private readonly api = inject(HyperionAssessmentCriteriaGenerationApi);

    generate(exercise: Exercise): Observable<GradingCriterion[]> {
        const { courseId, request } = this.buildGenerationCall(exercise);
        return this.api.generateAssessmentCriteria(courseId, request).pipe(
            map((response) =>
                response.criteria.map((generatedCriterion) => {
                    const criterion = new GradingCriterion();
                    criterion.title = generatedCriterion.title;
                    criterion.structuredGradingInstructions = generatedCriterion.structuredGradingInstructions.map((generatedInstruction) => {
                        const instruction = new GradingInstruction();
                        instruction.credits = generatedInstruction.credits;
                        instruction.gradingScale = generatedInstruction.gradingScale;
                        instruction.instructionDescription = generatedInstruction.instructionDescription;
                        instruction.feedback = generatedInstruction.feedback;
                        instruction.usageCount = generatedInstruction.usageCount;
                        return instruction;
                    });
                    return criterion;
                }),
            ),
        );
    }

    buildGenerationCall(exercise: Exercise): AssessmentCriteriaGenerationCall {
        const courseId = exercise.course?.id ?? exercise.exerciseGroup?.exam?.course?.id;
        if (courseId === undefined) {
            throw new Error('Assessment criteria generation requires a course');
        }
        if (exercise.type !== ExerciseType.TEXT && exercise.type !== ExerciseType.MODELING) {
            throw new Error('Assessment criteria generation supports only text and modeling exercises');
        }

        const request: AssessmentCriteriaGenerationRequest = {
            exerciseType:
                exercise.type === ExerciseType.TEXT ? AssessmentCriteriaGenerationRequestExerciseTypeEnum.Text : AssessmentCriteriaGenerationRequestExerciseTypeEnum.Modeling,
            problemStatement: exercise.problemStatement?.trim() ?? '',
            maxPoints: exercise.maxPoints!,
            bonusPoints: exercise.bonusPoints ?? 0,
            gradingInstructions: exercise.gradingInstructions?.trim() || undefined,
        };

        if (exercise.type === ExerciseType.MODELING) {
            const modelingExercise = exercise as ModelingExercise;
            request.modelingContext = {
                diagramType: String(modelingExercise.diagramType),
                exampleSolutionModel: modelingExercise.exampleSolutionModel,
            };
        }
        return { courseId, request };
    }
}
