import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { map, tap } from 'rxjs/operators';
import RewritingVariant from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-variant';
import { AlertService } from 'app/shared/service/alert.service';
import { ConsistencyCheckResult, RewriteResult } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence-results';
import { HyperionReviewAndRefineApiService } from 'app/openapi/api/hyperionReviewAndRefineApi.service';
import { ProblemStatementRewriteRequest } from 'app/openapi/model/problemStatementRewriteRequest';
import { ProblemStatementRewriteResponse } from 'app/openapi/model/problemStatementRewriteResponse';
import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';
import { Observable, finalize } from 'rxjs';

/**
 * Service providing shared functionality for Artemis Intelligence of the markdown editor.
 * This service is intended to be used by the AI actions of the Monaco editors.
 */
@Injectable({ providedIn: 'root' })
export class ArtemisIntelligenceService {
    public resourceUrl = 'api/nebula';

    private http = inject(HttpClient);
    private alertService = inject(AlertService);
    private hyperionApiService = inject(HyperionReviewAndRefineApiService);
    private isLoadingRewrite = signal<boolean>(false);
    private isLoadingConsistencyCheck = signal<boolean>(false);
    isLoading = computed(() => this.isLoadingRewrite() || this.isLoadingConsistencyCheck());

    /**
     * Triggers the rewriting pipeline via HTTP and subscribes to its WebSocket updates.
     * @param toBeRewritten The text to be rewritten.
     * @param rewritingVariant The variant for rewriting.
     * @param contextId The ID of the context (courseId for both Iris and Hyperion).
     * @return Observable that emits the rewritten text when available.
     */
    rewrite(toBeRewritten: string | undefined, rewritingVariant: RewritingVariant, contextId: number): Observable<RewriteResult> {
        this.isLoadingRewrite.set(true);

        if (rewritingVariant === RewritingVariant.FAQ) {
            return this.http
                .post<RewriteResult>(`${this.resourceUrl}/courses/${contextId}/rewrite-text`, {
                    toBeRewritten: toBeRewritten,
                })
                .pipe(finalize(() => this.isLoadingRewrite.set(false)));
        } else {
            // Use OpenAPI client for Hyperion rewriting (contextId is courseId)
            const request: ProblemStatementRewriteRequest = {
                problemStatementText: toBeRewritten || '',
            };

            return this.hyperionApiService.rewriteProblemStatement(contextId, request).pipe(
                map(
                    (response: ProblemStatementRewriteResponse): RewriteResult => ({
                        rewrittenText: response.rewrittenText,
                        inconsistencies: undefined,
                        suggestions: undefined,
                        improvement: response.improved ? 'Text was improved' : 'Text was not improved',
                    }),
                ),
                tap(() => {
                    this.alertService.success('artemisApp.markdownEditor.artemisIntelligence.alerts.rewrite.success');
                }),
                finalize(() => this.isLoadingRewrite.set(false)),
            );
        }
    }

    /**
     * Triggers the consistency check pipeline using the OpenAPI client.
     *
     * @param exerciseId The ID of the exercise to check for consistency.
     * @return Observable that emits the consistency check result.
     */
    consistencyCheck(exerciseId: number): Observable<ConsistencyCheckResponse> {
        this.isLoadingConsistencyCheck.set(true);
        return this.hyperionApiService.checkExerciseConsistency(exerciseId).pipe(finalize(() => this.isLoadingConsistencyCheck.set(false)));
    }

    /**
     * Triggers a consistency check for FAQ entries via HTTP.
     * @param courseId The ID of the course the FAQ belongs to.
     * @param toBeChecked The text of the FAQ entry to be checked for consistency.
     * @return Observable that emits the consistency check result.
     */
    faqConsistencyCheck(courseId: number, toBeChecked: string): Observable<ConsistencyCheckResult> {
        this.isLoadingRewrite.set(true);
        return this.http
            .post<ConsistencyCheckResult>(`${this.resourceUrl}/courses/${courseId}/consistency-check`, { toBeChecked: toBeChecked })
            .pipe(finalize(() => this.isLoadingRewrite.set(false)));
    }
}
