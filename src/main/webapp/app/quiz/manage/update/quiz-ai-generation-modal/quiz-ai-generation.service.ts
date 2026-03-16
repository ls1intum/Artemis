import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { HyperionQuizQuestionGenerationApiService } from 'app/openapi/api/hyperionQuizQuestionGenerationApi.service';
import { QuizQuestionGenerationRequest } from 'app/openapi/model/quizQuestionGenerationRequest';
import { GeneratedQuizQuestion } from 'app/openapi/model/generatedQuizQuestion';
import { GeneratedQuestion } from 'app/quiz/manage/update/quiz-ai-generation-modal/quiz-ai-generation.types';

@Injectable({ providedIn: 'root' })
export class QuizAiGenerationService {
    private hyperionQuizQuestionGenerationApiService = inject(HyperionQuizQuestionGenerationApiService);

    generateQuizQuestions(courseId: number, request: QuizQuestionGenerationRequest): Observable<GeneratedQuestion[]> {
        return this.hyperionQuizQuestionGenerationApiService
            .generateQuizQuestions(courseId, request)
            .pipe(map((response) => response.questions.map((question, index) => this.toGeneratedQuestion(question, index))));
    }

    private toGeneratedQuestion(question: GeneratedQuizQuestion, index: number): GeneratedQuestion {
        return {
            id: `${question.type}-${index}`,
            type: question.type,
            title: question.title,
            questionText: question.questionText,
            options: question.options.map((option) => ({
                text: option.text,
                correct: !!option.correct,
            })),
        };
    }
}
