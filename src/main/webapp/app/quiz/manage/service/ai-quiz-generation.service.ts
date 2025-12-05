import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { AiDifficultyLevel, AiLanguage, AiRequestedSubtype } from 'app/quiz/manage/service/ai-quiz-generation.enums';
import { AiGeneratedOptionDTO, AiGeneratedQuestionDTO, AiQuizGenerationRequest, AiQuizGenerationResponse } from 'app/quiz/manage/service/ai-quiz-generation.models';

/**
 * Public barrel exports so other code can still import all types
 * directly from this service module.
 */
export { AiLanguage, AiDifficultyLevel, AiRequestedSubtype, AiQuizGenerationRequest, AiGeneratedOptionDTO, AiGeneratedQuestionDTO, AiQuizGenerationResponse };

@Injectable({ providedIn: 'root' })
export class AiQuizGenerationService {
    private readonly http = inject(HttpClient);

    /**
     * POST api/hyperion/quizzes/courses/{courseId}/generate
     */
    generate(courseId: number, payload: AiQuizGenerationRequest): Observable<AiQuizGenerationResponse> {
        return this.http.post<AiQuizGenerationResponse>(`api/hyperion/quizzes/courses/${courseId}/generate`, payload).pipe(
            catchError((err: HttpErrorResponse) => {
                const message = this.describe(err);
                return of({ questions: [], warnings: [message] });
            }),
        );
    }

    private describe(err: HttpErrorResponse): string {
        if (err.status === 0) {
            return 'Network error contacting the AI generation endpoint.';
        }
        if (err.status === 404) {
            return 'AI generation is not available on this server (Hyperion disabled).';
        }

        const serverMessage = (err.error && (err.error.error || err.error.title || err.error.message)) || (typeof err.error === 'string' ? err.error : null);

        return `Generation failed (HTTP ${err.status}).${serverMessage ? ` ${serverMessage}` : ''}`;
    }
}
