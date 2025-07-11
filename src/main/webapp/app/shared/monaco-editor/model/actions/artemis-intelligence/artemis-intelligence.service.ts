import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { finalize, tap } from 'rxjs/operators';
import RewritingVariant from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-variant';
import { AlertService } from 'app/shared/service/alert.service';

/**
 * Service providing shared functionality for Artemis Intelligence of the markdown editor.
 * This service is intended to be used by the AI actions of the Monaco editors.
 */
@Injectable({ providedIn: 'root' })
export class ArtemisIntelligenceService {
    public resourceUrl = 'api/iris';
    public hyperionResourceUrl = 'api/hyperion';

    private http = inject(HttpClient);
    private alertService = inject(AlertService);

    private isLoadingRewrite = signal<boolean>(false);
    private isLoadingConsistencyCheck = signal<boolean>(false);
    isLoading = computed(() => this.isLoadingRewrite() || this.isLoadingConsistencyCheck());

    /**
     * Triggers the rewriting pipeline via HTTP and returns the result directly.
     * @param toBeRewritten The text to be rewritten.
     * @param rewritingVariant The variant for rewriting.
     * @param courseId The ID of the course to which the rewritten text belongs.
     * @return Observable that emits the rewritten text when available.
     */
    rewrite(toBeRewritten: string, rewritingVariant: RewritingVariant, courseId: number): Observable<string> {
        this.isLoadingRewrite.set(true);

        // Use Hyperion for PROBLEM_STATEMENT rewriting, Iris for FAQ rewriting
        const baseUrl = rewritingVariant === RewritingVariant.FAQ ? this.resourceUrl : this.hyperionResourceUrl;
        const endpoint =
            rewritingVariant === RewritingVariant.FAQ
                ? `${baseUrl}/courses/${courseId}/rewrite-text`
                : `${baseUrl}/review-and-refine/courses/${courseId}/rewrite-problem-statement`;

        const requestBody = rewritingVariant === RewritingVariant.FAQ ? { toBeRewritten: toBeRewritten, variant: rewritingVariant } : { text: toBeRewritten };

        return this.http.post(endpoint, requestBody, { responseType: 'text' }).pipe(
            tap(() => {
                this.alertService.success('artemisApp.markdownEditor.artemisIntelligence.alerts.rewrite.success');
            }),
            finalize(() => this.isLoadingRewrite.set(false)),
        );
    }

    /**
     * Triggers the consistency check pipeline via HTTP and returns the result directly.
     *
     * @param exerciseId The ID of the exercise to check for consistency.
     * @return Observable that emits the consistency check result immediately.
     */
    consistencyCheck(exerciseId: number): Observable<string> {
        this.isLoadingConsistencyCheck.set(true);
        return this.http
            .post(`${this.hyperionResourceUrl}/review-and-refine/exercises/${exerciseId}/check-consistency`, null, { responseType: 'text' })
            .pipe(finalize(() => this.isLoadingConsistencyCheck.set(false)));
    }
}
