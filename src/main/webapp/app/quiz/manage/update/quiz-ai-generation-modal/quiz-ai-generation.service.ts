import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { HyperionQuizQuestionGenerationApiService } from 'app/openapi/api/hyperionQuizQuestionGenerationApi.service';
import { QuizQuestionGenerationRequest } from 'app/openapi/model/quizQuestionGenerationRequest';
import { GeneratedQuizQuestion } from 'app/openapi/model/generatedQuizQuestion';
import { QuizQuestionRefinementRequest } from 'app/openapi/model/quizQuestionRefinementRequest';
import { GeneratedQuestion } from 'app/quiz/manage/update/quiz-ai-generation-modal/quiz-ai-generation.types';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';

@Injectable({ providedIn: 'root' })
export class QuizAiGenerationService {
    private hyperionQuizQuestionGenerationApiService = inject(HyperionQuizQuestionGenerationApiService);

    generateQuizQuestions(courseId: number, request: QuizQuestionGenerationRequest): Observable<GeneratedQuestion[]> {
        return this.hyperionQuizQuestionGenerationApiService
            .generateQuizQuestions(courseId, request)
            .pipe(map((response) => response.questions.map((question, index) => this.toGeneratedQuestion(question, index))));
    }

    refineMultipleChoiceQuestion(
        courseId: number,
        question: MultipleChoiceQuestion,
        refinementPrompt: string,
    ): Observable<{ refinedQuestion: MultipleChoiceQuestion; reasoning: string }> {
        const request: QuizQuestionRefinementRequest = {
            question: {
                type: (question.singleChoice ? 'single-choice' : 'multiple-choice') as GeneratedQuizQuestion.TypeEnum,
                title: question.title?.trim() || 'Untitled Question',
                questionText: question.text ?? '',
                hint: question.hint ?? undefined,
                explanation: question.explanation ?? undefined,
                options: (question.answerOptions ?? []).map((opt) => ({
                    text: opt.text ?? '',
                    correct: !!opt.isCorrect,
                    hint: opt.hint ?? undefined,
                    explanation: opt.explanation ?? undefined,
                })),
            },
            refinementPrompt,
        };

        return this.hyperionQuizQuestionGenerationApiService.refineQuizQuestion(courseId, request).pipe(
            map((response) => ({
                refinedQuestion: this.applyRefinedContentToQuestion(question, this.toGeneratedQuestion(response.question, 0)),
                reasoning: response.reasoning,
            })),
        );
    }

    private applyRefinedContentToQuestion(original: MultipleChoiceQuestion, refined: GeneratedQuestion): MultipleChoiceQuestion {
        original.title = refined.title;
        original.text = refined.questionText;
        original.hint = refined.hint;
        original.explanation = refined.explanation;
        original.singleChoice = refined.type !== 'multiple-choice';
        if (original.singleChoice) {
            original.scoringType = ScoringType.ALL_OR_NOTHING;
        }
        original.answerOptions = refined.options.map((opt) => {
            const answerOption = new AnswerOption();
            answerOption.text = opt.text;
            answerOption.isCorrect = opt.correct;
            answerOption.hint = opt.hint;
            answerOption.explanation = opt.explanation;
            answerOption.question = original;
            return answerOption;
        });
        original.hasCorrectOption = original.answerOptions.some((opt) => !!opt.isCorrect);
        return original;
    }

    private toGeneratedQuestion(question: GeneratedQuizQuestion, index: number): GeneratedQuestion {
        return {
            id: `${question.type}-${index}`,
            type: question.type,
            title: question.title,
            questionText: question.questionText,
            hint: question.hint ?? undefined,
            explanation: question.explanation ?? undefined,
            options: question.options.map((option) => ({
                text: option.text,
                correct: !!option.correct,
                hint: option.hint ?? undefined,
                explanation: option.explanation ?? undefined,
            })),
        };
    }
}
