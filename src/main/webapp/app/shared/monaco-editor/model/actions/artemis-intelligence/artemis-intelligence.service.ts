import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { finalize, map, tap } from 'rxjs/operators';
import RewritingVariant from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-variant';
import { AlertService } from 'app/shared/service/alert.service';
import { RewriteResult } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-result';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { ProblemStatementRewriteRequest } from 'app/openapi/model/problemStatementRewriteRequest';
import { ProblemStatementRewriteResponse } from 'app/openapi/model/problemStatementRewriteResponse';
import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';
import { HyperionFaqApiService } from 'app/openapi/api/hyperionFaqApi.service';
import { RewriteFaqRequest } from 'app/openapi/model/rewriteFaqRequest';
import { RewriteFaqResponse } from 'app/openapi/model/rewriteFaqResponse';
/**
 * Service providing shared functionality for Artemis Intelligence of the markdown editor.
 * This service is intended to be used by the AI actions of the Monaco editors.
 */
@Injectable({ providedIn: 'root' })
export class ArtemisIntelligenceService {
    private alertService = inject(AlertService);
    private hyperionProblemStatementApiService = inject(HyperionProblemStatementApiService);
    private hyperionFaqApiService = inject(HyperionFaqApiService);

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

        // Use OpenAPI client for Hyperion rewriting (contextId is courseId)
        if (rewritingVariant === RewritingVariant.FAQ) {
            const request: RewriteFaqRequest = {
                faqText: toBeRewritten || '',
            };
            return this.hyperionFaqApiService.rewriteFaq(contextId, request).pipe(
                map(
                    (response: RewriteFaqResponse): RewriteResult => ({
                        result: response.rewrittenText,
                        inconsistencies: response.inconsistencies,
                        suggestions: response.suggestions,
                        improvement: response.improvement,
                    }),
                ),
                tap(() => {
                    this.alertService.success('artemisApp.markdownEditor.artemisIntelligence.alerts.rewrite.success');
                }),
                finalize(() => this.isLoadingRewrite.set(false)),
            );
        } else {
            const request: ProblemStatementRewriteRequest = {
                problemStatementText: toBeRewritten || '',
            };

            return this.hyperionProblemStatementApiService.rewriteProblemStatement(contextId, request).pipe(
                map(
                    (response: ProblemStatementRewriteResponse): RewriteResult => ({
                        result: response.rewrittenText,
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
        return this.hyperionProblemStatementApiService.checkExerciseConsistency(exerciseId).pipe(finalize(() => this.isLoadingConsistencyCheck.set(false)));
    }
}
