import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, WritableSignal, inject } from '@angular/core';
import { Observable, OperatorFunction, catchError, finalize, map, of } from 'rxjs';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { FileService } from 'app/shared/service/file.service';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { AlertService } from 'app/shared/service/alert.service';
import {
    buildGenerationRequest,
    buildGlobalRefinementRequest,
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
    loadTemplate(exercise: ProgrammingExercise | undefined): Observable<{ template: string; loaded: boolean }> {
        if (!exercise?.programmingLanguage) {
            return of({ template: '', loaded: false });
        }
        return this.fileService.getTemplateFile(exercise.programmingLanguage, exercise.projectType).pipe(
            map((template) => ({ template, loaded: true })),
            catchError(() => of({ template: '', loaded: false })),
        );
    }

    /** Generates a problem statement using the provided prompt. */
    generateProblemStatement(exercise: ProgrammingExercise | undefined, prompt: string, loadingSignal: WritableSignal<boolean>): Observable<OperationResult> {
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
    refineGlobally(exercise: ProgrammingExercise | undefined, currentContent: string, prompt: string, loadingSignal: WritableSignal<boolean>): Observable<OperationResult> {
        const courseId = getCourseId(exercise);
        if (!courseId || !prompt?.trim() || !currentContent?.trim()) {
            const emptyContent = !currentContent?.trim();
            if (emptyContent) {
                this.alertService.error('artemisApp.programmingExercise.problemStatement.refinementError');
            }
            return of({ success: false, errorHandled: emptyContent });
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
