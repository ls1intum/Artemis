import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { HyperionAssessmentCriteriaGenerationApi } from 'app/openapi/api/hyperion-assessment-criteria-generation-api';
import { AssessmentCriteriaGenerationRequest } from 'app/openapi/model/assessment-criteria-generation-request';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';

export interface AssessmentCriteriaGenerationCall {
    courseId: number;
    request: AssessmentCriteriaGenerationRequest;
}

export interface AssessmentCriteriaGenerationContext {
    exampleSolution?: string;
    additionalContext?: string;
}

@Injectable({ providedIn: 'root' })
export class AssessmentCriteriaGenerationService {
    private readonly api = inject(HyperionAssessmentCriteriaGenerationApi);

    generate(exercise: Exercise, context: AssessmentCriteriaGenerationContext = {}): Observable<GradingCriterion[]> {
        const { courseId, request } = this.buildGenerationCall(exercise, context);
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

    buildGenerationCall(exercise: Exercise, context: AssessmentCriteriaGenerationContext = {}): AssessmentCriteriaGenerationCall {
        const courseId = exercise.course?.id ?? exercise.exerciseGroup?.exam?.course?.id;
        if (courseId === undefined) {
            throw new Error('Assessment criteria generation requires a course');
        }

        const request: AssessmentCriteriaGenerationRequest = {
            problemStatement: exercise.problemStatement?.trim() ?? '',
            maxPoints: exercise.maxPoints!,
            bonusPoints: exercise.bonusPoints ?? 0,
        };
        const gradingInstructions = exercise.gradingInstructions?.trim();
        if (gradingInstructions) {
            request.gradingInstructions = gradingInstructions;
        }
        const exampleSolution = context.exampleSolution?.trim();
        if (exampleSolution) {
            request.exampleSolution = exampleSolution;
        }
        const additionalContext = context.additionalContext?.trim();
        if (additionalContext) {
            request.additionalContext = additionalContext;
        }

        return { courseId, request };
    }
}
