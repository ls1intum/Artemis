import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

export enum AiLanguage {
    ENGLISH = 'ENGLISH',
    GERMAN = 'GERMAN',
}
export enum AiDifficultyLevel {
    EASY = 'EASY',
    MEDIUM = 'MEDIUM',
    HARD = 'HARD',
}
export enum AiRequestedSubtype {
    SINGLE_CORRECT = 'SINGLE_CORRECT',
    MULTI_CORRECT = 'MULTI_CORRECT',
    TRUE_FALSE = 'TRUE_FALSE',
}

export interface AiQuizGenerationRequest {
    numberOfQuestions: number;
    language: AiLanguage;
    topic: string;
    promptHint?: string | null;
    difficultyLevel: AiDifficultyLevel;
    requestedSubtype: AiRequestedSubtype;
}

export interface AiGeneratedOptionDTO {
    text: string;
    correct: boolean;
    feedback?: string | null;
}

export interface AiGeneratedQuestionDTO {
    title: string;
    text: string;
    explanation?: string | null;
    hint?: string | null;
    difficulty?: number | null;
    tags: string[];
    subtype: AiRequestedSubtype;
    competencyIds: number[];
    options: AiGeneratedOptionDTO[];
}

export interface AiQuizGenerationResponse {
    questions: AiGeneratedQuestionDTO[];
    warnings?: string[];
}

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
        if (err.status === 0) return 'Network error contacting the AI generation endpoint.';
        if (err.status === 404) return 'AI generation is not available on this server (Hyperion disabled).';
        const server = (err.error && (err.error.error || err.error.title || err.error.message)) || (typeof err.error === 'string' ? err.error : null);
        return `Generation failed (HTTP ${err.status}).${server ? ` ${server}` : ''}`;
    }
}
