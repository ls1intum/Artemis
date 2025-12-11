import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { AiQuizGenerationRequest, AiQuizGenerationResponse } from 'app/quiz/manage/service/ai-quiz-generation.models';

export { AiLanguage, AiDifficultyLevel, AiRequestedSubtype } from 'app/quiz/manage/service/ai-quiz-generation.enums';
export type { AiQuizGenerationRequest, AiGeneratedOptionDTO, AiGeneratedQuestionDTO, AiQuizGenerationResponse } from 'app/quiz/manage/service/ai-quiz-generation.models';

@Injectable({ providedIn: 'root' })
export class AiQuizGenerationService {
    private readonly http = inject(HttpClient);

    /**
     * Sends a quiz generation request to the Hyperion server
     * POST api/hyperion/quizzes/courses/{courseId}/generate
     */
    generate(courseId: number, payload: AiQuizGenerationRequest): Observable<AiQuizGenerationResponse> {
        return this.http.post<AiQuizGenerationResponse>(`api/hyperion/quizzes/courses/${courseId}/generate`, payload);
    }
}
