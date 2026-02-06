import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
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
    generateProblemStatement(exercise: ProgrammingExercise | undefined, prompt: string, setLoading: (loading: boolean) => void): Observable<OperationResult> {
        const courseId = getCourseId(exercise);
        if (!courseId || !prompt?.trim()) {
            return of({ success: false, errorHandled: true });
        }
        setLoading(true);
        return this.hyperionApiService
            .generateProblemStatement(courseId, buildGenerationRequest(prompt))
            .pipe(
                this.handleApiResponse(
                    setLoading,
                    'artemisApp.programmingExercise.problemStatement.generationSuccess',
                    'artemisApp.programmingExercise.problemStatement.generationError',
                    isValidGenerationResponse,
                    (response) => response?.draftProblemStatement,
                ),
            );
    }

    /** Refines a problem statement globally using the provided prompt. */
    refineGlobally(exercise: ProgrammingExercise | undefined, currentContent: string, prompt: string, setLoading: (loading: boolean) => void): Observable<OperationResult> {
        const courseId = getCourseId(exercise);
        if (!courseId || !prompt?.trim() || !currentContent?.trim()) {
            const emptyContent = !currentContent?.trim();
            if (emptyContent) {
                this.alertService.error('artemisApp.programmingExercise.problemStatement.cannotRefineEmpty');
            }
            return of({ success: false, errorHandled: true });
        }
        if (currentContent.length > MAX_PROBLEM_STATEMENT_LENGTH) {
            this.alertService.error('artemisApp.programmingExercise.problemStatement.problemStatementTooLong');
            return of({ success: false, errorHandled: true });
        }
        setLoading(true);
        return this.hyperionApiService
            .refineProblemStatementGlobally(courseId, buildGlobalRefinementRequest(currentContent, prompt))
            .pipe(
                this.handleApiResponse(
                    setLoading,
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
        setLoading: (loading: boolean) => void,
        successKey: string,
        errorKey: string,
        isValid: (response: T) => boolean,
        getContent: (response: T) => string | undefined,
    ): OperatorFunction<T, OperationResult> {
        return (source) =>
            source.pipe(
                map((response) => {
                    const success = isValid(response);
                    if (success) {
                        this.alertService.success(successKey);
                    } else {
                        this.alertService.error(errorKey);
                    }
                    return { success, content: getContent(response), errorHandled: !success };
                }),
                catchError((error) => {
                    const handledByInterceptor = error instanceof HttpErrorResponse && !!(error.error?.errorKey || error.error?.title);
                    return of({ success: false, errorHandled: handledByInterceptor });
                }),
                finalize(() => setLoading(false)),
            );
    }
}
