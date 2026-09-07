import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { HyperionAssessmentCriteriaGenerationApi } from 'app/openapi/api/hyperion-assessment-criteria-generation-api';
import { AssessmentCriteriaGenerationRequest } from 'app/openapi/model/assessment-criteria-generation-request';
import { Exercise, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';

/** Fully prepared assessment-criteria generation call. */
export interface AssessmentCriteriaGenerationCall {
    /** Course whose configuration and permissions govern the generation request. */
    courseId: number;
    /** Sanitized exercise context sent to the generation endpoint. */
    request: AssessmentCriteriaGenerationRequest;
}

/** Optional exercise-type-specific context included in an assessment-criteria request. */
export interface AssessmentCriteriaGenerationContext {
    /** Example solution supplied by exercise types that expose one. */
    exampleSolution?: string;
    /** Additional serialized context that helps Hyperion interpret the exercise. */
    additionalContext?: string;
}

/** Builds assessment-criteria generation requests and maps generated DTOs to Artemis entities. */
@Injectable({ providedIn: 'root' })
export class AssessmentCriteriaGenerationService {
    private readonly api = inject(HyperionAssessmentCriteriaGenerationApi);

    /**
     * Generates structured grading criteria for an exercise.
     * @param exercise Exercise whose current content should be used for generation.
     * @param context Optional exercise-type-specific example solution and context.
     * @returns Generated criteria represented as unsaved Artemis entities.
     */
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

    /**
     * Creates the API request from the current exercise state.
     * @param exercise Exercise whose content should be serialized.
     * @param context Optional exercise-type-specific example solution and context.
     * @returns Course identifier and request body required by the generated API client.
     * @throws When the exercise does not belong to a course or has no valid maximum score.
     */
    buildGenerationCall(exercise: Exercise, context: AssessmentCriteriaGenerationContext = {}): AssessmentCriteriaGenerationCall {
        const courseId = exercise.course?.id ?? exercise.exerciseGroup?.exam?.course?.id;
        if (courseId === undefined) {
            throw new Error('Assessment criteria generation requires a course');
        }
        const maxPoints = exercise.maxPoints;
        if (maxPoints === undefined || !Number.isFinite(maxPoints) || maxPoints <= 0) {
            throw new Error('Assessment criteria generation requires a valid maximum score');
        }

        const request: AssessmentCriteriaGenerationRequest = {
            problemStatement: exercise.problemStatement?.trim() ?? '',
            maxPoints,
            bonusPoints: exercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_COMPLETELY ? (exercise.bonusPoints ?? 0) : 0,
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
