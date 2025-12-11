import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { finalize, map, tap } from 'rxjs/operators';
import RewritingVariant from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-variant';
import { AlertService } from 'app/shared/service/alert.service';
import { RewriteResult } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-result';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { HyperionReviewAndRefineApiService } from 'app/openapi/api/hyperionReviewAndRefineApi.service';
import { ProblemStatementRewriteRequest } from 'app/openapi/model/problemStatementRewriteRequest';
import { ProblemStatementRewriteResponse } from 'app/openapi/model/problemStatementRewriteResponse';
import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';

/**
 * DTO for the Iris rewriting feature.
 * Pyris sends callback updates back to Artemis during the rewriting of text.
 * These updates contain the current status of the rewriting process,
 * which is then forwarded to the user via WebSockets.
 */
export interface PyrisRewritingStatusUpdateDTO {
    /** List of stages of the generation process */
    stages: PyrisStageDTO[];

    /** The result of the rewriting process so far */
    result: string;

    /** Optional list of inconsistencies detected during rewriting */
    inconsistencies?: string[];

    /** Optional list of suggestions for improvement */
    suggestions?: string[];

    /** Optional overall improvement message */
    improvement?: string; // undefined preferred over null
}

/**
 * One stage of the rewriting pipeline.
 */
export interface PyrisStageDTO {
    name: string;
    weight: number;
    state: PyrisStageState;
    message: string;
    internal: boolean; // defaultValue = false → still required field
}

/**
 * State of a stage in the rewriting pipeline.
 */
export enum PyrisStageState {
    NOT_STARTED = 'NOT_STARTED',
    IN_PROGRESS = 'IN_PROGRESS',
    DONE = 'DONE',
    SKIPPED = 'SKIPPED',
    ERROR = 'ERROR',
}

/**
 * Service providing shared functionality for Artemis Intelligence of the markdown editor.
 * This service is intended to be used by the AI actions of the Monaco editors.
 */
@Injectable({ providedIn: 'root' })
export class ArtemisIntelligenceService {
    public resourceUrl = 'api/iris';

    private http = inject(HttpClient);
    private alertService = inject(AlertService);
    private websocketService = inject(WebsocketService);
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
            // Use WebSocket approach for FAQ rewriting via Iris
            return new Observable<RewriteResult>((observer) => {
                this.http
                    .post(`${this.resourceUrl}/courses/${contextId}/rewrite-text`, {
                        toBeRewritten: toBeRewritten,
                        variant: rewritingVariant,
                    })
                    .subscribe({
                        next: () => {
                            const websocketTopic = `/user/topic/iris/rewriting/${contextId}`;
                            const websocketSubscription: Subscription = this.websocketService.subscribe<PyrisRewritingStatusUpdateDTO>(websocketTopic).subscribe({
                                next: (update: PyrisRewritingStatusUpdateDTO) => {
                                    if (update.result) {
                                        observer.next({
                                            result: update.result || undefined,
                                            inconsistencies: update.inconsistencies || [],
                                            suggestions: update.suggestions || [],
                                            improvement: update.improvement || '',
                                        });
                                        observer.complete();
                                        this.isLoadingRewrite.set(false);
                                        websocketSubscription.unsubscribe();
                                        this.alertService.success('artemisApp.markdownEditor.artemisIntelligence.alerts.rewrite.success');
                                    }
                                },
                                error: (error: unknown) => {
                                    observer.error(error);
                                    this.isLoadingRewrite.set(false);
                                    websocketSubscription.unsubscribe();
                                },
                            });
                        },
                        error: (error) => {
                            this.isLoadingRewrite.set(false);
                            observer.error(error);
                        },
                    });
            });
        } else {
            // Use OpenAPI client for Hyperion rewriting (contextId is courseId)
            const request: ProblemStatementRewriteRequest = {
                problemStatementText: toBeRewritten || '',
            };

            return this.hyperionApiService.rewriteProblemStatement(contextId, request).pipe(
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
        return this.hyperionApiService.checkExerciseConsistency(exerciseId).pipe(finalize(() => this.isLoadingConsistencyCheck.set(false)));
    }
}
