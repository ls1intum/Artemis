import { Injectable, WritableSignal, inject } from '@angular/core';
import { Observable, finalize, map, of, tap } from 'rxjs';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { FileService } from 'app/shared/service/file.service';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ProblemStatementRefinementResponse } from 'app/openapi/model/problemStatementRefinementResponse';
import { ProblemStatementGenerationResponse } from 'app/openapi/model/problemStatementGenerationResponse';
import {
    InlineRefinementEvent,
    buildGenerationRequest,
    buildGlobalRefinementRequest,
    buildTargetedRefinementRequest,
    getCourseId,
    isValidGenerationResponse,
    isValidRefinementResponse,
} from 'app/programming/manage/shared/problem-statement.utils';

/**
 * Result of a problem statement generation operation.
 */
export interface GenerationResult {
    success: boolean;
    content?: string;
}

/**
 * Result of a problem statement refinement operation.
 */
export interface RefinementResult {
    success: boolean;
    content?: string;
}

/**
 * Service that centralizes problem statement generation, refinement, and template loading operations.
 * This eliminates duplicate API subscription logic across components.
 *
 * Components call these methods and handle the result to update their local state.
 * The service handles:
 * - Request building
 * - API calls
 * - Response validation
 * - Alert messages (success/error)
 * - Loading state signal management (via finalize)
 */
@Injectable({
    providedIn: 'root',
})
export class ProblemStatementService {
    private readonly fileService = inject(FileService);
    private readonly hyperionApiService = inject(HyperionProblemStatementApiService);
    private readonly alertService = inject(AlertService);

    /**
     * Loads the template problem statement for the given exercise.
     * Updates the provided signals with the result.
     *
     * @param exercise The programming exercise to load the template for
     * @param templateSignal Signal to update with the template content
     * @param loadedSignal Signal to update with the loaded state
     */
    loadTemplate(exercise: ProgrammingExercise | undefined, templateSignal: WritableSignal<string>, loadedSignal: WritableSignal<boolean>): void {
        if (!exercise?.programmingLanguage) {
            templateSignal.set('');
            loadedSignal.set(false);
            return;
        }

        this.fileService.getTemplateFile(exercise.programmingLanguage, exercise.projectType).subscribe({
            next: (template) => {
                templateSignal.set(template);
                loadedSignal.set(true);
            },
            error: () => {
                templateSignal.set('');
                loadedSignal.set(false);
            },
        });
    }

    /**
     * Generates a problem statement using the provided prompt.
     * Handles API call, validation, alerts, and loading state.
     *
     * @param exercise The programming exercise
     * @param prompt The user's prompt for generation
     * @param loadingSignal Signal to update during the operation
     * @returns Observable of GenerationResult - caller handles the state updates
     */
    generateProblemStatement(exercise: ProgrammingExercise | undefined, prompt: string, loadingSignal: WritableSignal<boolean>): Observable<GenerationResult> {
        const courseId = getCourseId(exercise);

        if (!courseId || !prompt?.trim()) {
            return of({ success: false });
        }

        loadingSignal.set(true);
        const request = buildGenerationRequest(prompt);

        return this.hyperionApiService.generateProblemStatement(courseId, request).pipe(
            finalize(() => loadingSignal.set(false)),
            map((response: ProblemStatementGenerationResponse) => {
                const success = isValidGenerationResponse(response);
                if (success) {
                    this.alertService.success('artemisApp.programmingExercise.problemStatement.generationSuccess');
                } else {
                    this.alertService.error('artemisApp.programmingExercise.problemStatement.generationError');
                }
                return {
                    success,
                    content: response?.draftProblemStatement,
                };
            }),
            tap({
                error: () => {
                    this.alertService.error('artemisApp.programmingExercise.problemStatement.generationError');
                },
            }),
        );
    }

    /**
     * Refines a problem statement globally using the provided prompt.
     * Handles API call, validation, alerts, and loading state.
     *
     * @param exercise The programming exercise
     * @param currentContent The current problem statement content
     * @param prompt The user's refinement prompt
     * @param loadingSignal Signal to update during the operation
     * @returns Observable of RefinementResult - caller handles the state updates
     */
    refineGlobally(exercise: ProgrammingExercise | undefined, currentContent: string, prompt: string, loadingSignal: WritableSignal<boolean>): Observable<RefinementResult> {
        const courseId = getCourseId(exercise);

        if (!courseId || !prompt?.trim() || !currentContent?.trim()) {
            if (!currentContent?.trim()) {
                this.alertService.error('artemisApp.programmingExercise.inlineRefine.error');
            }
            return of({ success: false });
        }

        loadingSignal.set(true);
        const request = buildGlobalRefinementRequest(currentContent, prompt);

        return this.hyperionApiService.refineProblemStatementGlobally(courseId, request).pipe(
            finalize(() => loadingSignal.set(false)),
            map((response: ProblemStatementRefinementResponse) => {
                const success = isValidRefinementResponse(response);
                if (success) {
                    this.alertService.success('artemisApp.programmingExercise.inlineRefine.success');
                } else {
                    this.alertService.error('artemisApp.programmingExercise.inlineRefine.error');
                }
                return {
                    success,
                    content: response?.refinedProblemStatement,
                };
            }),
            tap({
                error: () => {
                    this.alertService.error('artemisApp.programmingExercise.inlineRefine.error');
                },
            }),
        );
    }

    /**
     * Refines a problem statement with targeted selection-based instructions.
     * Handles API call, validation, alerts, and loading state.
     *
     * @param exercise The programming exercise
     * @param currentContent The current problem statement content
     * @param event The inline refinement event with selection details
     * @param loadingSignal Signal to update during the operation
     * @returns Observable of RefinementResult - caller handles the state updates
     */
    refineTargeted(
        exercise: ProgrammingExercise | undefined,
        currentContent: string,
        event: InlineRefinementEvent,
        loadingSignal: WritableSignal<boolean>,
    ): Observable<RefinementResult> {
        const courseId = getCourseId(exercise);

        if (!courseId || !currentContent?.trim()) {
            this.alertService.error('artemisApp.programmingExercise.inlineRefine.error');
            return of({ success: false });
        }

        loadingSignal.set(true);
        const request = buildTargetedRefinementRequest(currentContent, event);

        return this.hyperionApiService.refineProblemStatementTargeted(courseId, request).pipe(
            finalize(() => loadingSignal.set(false)),
            map((response: ProblemStatementRefinementResponse) => {
                const success = isValidRefinementResponse(response);
                if (success) {
                    this.alertService.success('artemisApp.programmingExercise.inlineRefine.success');
                } else {
                    this.alertService.error('artemisApp.programmingExercise.inlineRefine.error');
                }
                return {
                    success,
                    content: response?.refinedProblemStatement,
                };
            }),
            tap({
                error: () => {
                    this.alertService.error('artemisApp.programmingExercise.inlineRefine.error');
                },
            }),
        );
    }
}
