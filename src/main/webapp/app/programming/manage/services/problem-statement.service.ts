import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, WritableSignal, inject } from '@angular/core';
import { Observable, OperatorFunction, catchError, finalize, map, of } from 'rxjs';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { FileService } from 'app/shared/service/file.service';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { AlertService } from 'app/shared/service/alert.service';
import {
    InlineRefinementEvent,
    buildGenerationRequest,
    buildGlobalRefinementRequest,
    buildTargetedRefinementRequest,
    getCourseId,
    isValidGenerationResponse,
    isValidRefinementResponse,
} from 'app/programming/manage/shared/problem-statement.utils';

/** Result of a problem statement operation (generation or refinement). */
export interface OperationResult {
    success: boolean;
    content?: string;
    errorHandled?: boolean;
}

// Type aliases for backward compatibility
export type GenerationResult = OperationResult;
export type RefinementResult = OperationResult;

/**
 * Service that centralizes problem statement generation, refinement, and template loading operations.
 * Eliminates duplicate API subscription logic across components.
 */
@Injectable({ providedIn: 'root' })
export class ProblemStatementService {
    private readonly fileService = inject(FileService);
    private readonly hyperionApiService = inject(HyperionProblemStatementApiService);
    private readonly alertService = inject(AlertService);

    /** Loads the template problem statement for the given exercise. */
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

    /** Generates a problem statement using the provided prompt. */
    generateProblemStatement(exercise: ProgrammingExercise | undefined, prompt: string, loadingSignal: WritableSignal<boolean>): Observable<GenerationResult> {
        const courseId = getCourseId(exercise);
        if (!courseId || !prompt?.trim()) {
            return of({ success: false });
        }
        loadingSignal.set(true);
        return this.hyperionApiService
            .generateProblemStatement(courseId, buildGenerationRequest(prompt))
            .pipe(
                this.handleApiResponse(
                    loadingSignal,
                    'artemisApp.programmingExercise.problemStatement.generationSuccess',
                    'artemisApp.programmingExercise.problemStatement.generationError',
                    isValidGenerationResponse,
                    (response) => response?.draftProblemStatement,
                ),
            );
    }

    /** Refines a problem statement globally using the provided prompt. */
    refineGlobally(exercise: ProgrammingExercise | undefined, currentContent: string, prompt: string, loadingSignal: WritableSignal<boolean>): Observable<RefinementResult> {
        const courseId = getCourseId(exercise);
        if (!courseId || !prompt?.trim() || !currentContent?.trim()) {
            if (!currentContent?.trim()) {
                this.alertService.error('artemisApp.programmingExercise.problemStatement.refinementError');
            }
            return of({ success: false });
        }
        loadingSignal.set(true);
        return this.hyperionApiService
            .refineProblemStatementGlobally(courseId, buildGlobalRefinementRequest(currentContent, prompt))
            .pipe(
                this.handleApiResponse(
                    loadingSignal,
                    'artemisApp.programmingExercise.problemStatement.refinementSuccess',
                    'artemisApp.programmingExercise.problemStatement.refinementError',
                    isValidRefinementResponse,
                    (response) => response?.refinedProblemStatement,
                ),
            );
    }

    /** Refines a problem statement with targeted selection-based instructions. */
    refineTargeted(
        exercise: ProgrammingExercise | undefined,
        currentContent: string,
        event: InlineRefinementEvent,
        loadingSignal: WritableSignal<boolean>,
    ): Observable<RefinementResult> {
        const courseId = getCourseId(exercise);
        if (!courseId || !currentContent?.trim()) {
            this.alertService.error('artemisApp.programmingExercise.problemStatement.inlineRefinement.error');
            return of({ success: false });
        }
        loadingSignal.set(true);
        return this.hyperionApiService
            .refineProblemStatementTargeted(courseId, buildTargetedRefinementRequest(currentContent, event))
            .pipe(
                this.handleApiResponse(
                    loadingSignal,
                    'artemisApp.programmingExercise.problemStatement.inlineRefinement.success',
                    'artemisApp.programmingExercise.problemStatement.inlineRefinement.error',
                    isValidRefinementResponse,
                    (r) => r?.refinedProblemStatement,
                ),
            );
    }

    /** Shared pipe operator for handling API responses with consistent loading, alerts, and error handling. */
    private handleApiResponse<T>(
        loadingSignal: WritableSignal<boolean>,
        successKey: string,
        errorKey: string,
        isValid: (response: T) => boolean,
        getContent: (response: T) => string | undefined,
    ): OperatorFunction<T, OperationResult> {
        return (source) =>
            source.pipe(
                finalize(() => loadingSignal.set(false)),
                map((response) => {
                    const success = isValid(response);
                    if (success) {
                        this.alertService.success(successKey);
                    }
                    return { success, content: getContent(response) };
                }),
                catchError((error) => {
                    const handledByInterceptor = error instanceof HttpErrorResponse && !!error.error?.errorKey;
                    return of({ success: false, errorHandled: handledByInterceptor });
                }),
            );
    }
}
