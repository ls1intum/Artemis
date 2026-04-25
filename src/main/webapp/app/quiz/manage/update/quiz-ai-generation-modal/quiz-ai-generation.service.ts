import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { HyperionQuizQuestionGenerationApiService } from 'app/openapi/api/hyperionQuizQuestionGenerationApi.service';
import { GeneratedQuizQuestion } from 'app/openapi/model/generatedQuizQuestion';
import { QuizQuestionRefinementRequest } from 'app/openapi/model/quizQuestionRefinementRequest';
import { QuizQuestionGenerationRequest } from 'app/openapi/model/quizQuestionGenerationRequest';
import { QuizQuestionBulkRefinementRequest } from 'app/openapi/model/quizQuestionBulkRefinementRequest';
import { QuizQuestionRefinementSuccessDTO } from 'app/openapi/model/quizQuestionRefinementSuccessDTO';
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

    /**
     * Sends a single multiple-choice question to Hyperion for AI-driven refinement.
     *
     * @param courseId the id of the course the quiz belongs to
     * @param question the multiple-choice question to refine
     * @param refinementPrompt user instructions describing how the question should change
     * @returns an observable that emits the refined question and the AI reasoning string
     */
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

    /**
     * Sends all provided multiple-choice questions to Hyperion for bulk AI-driven refinement using one shared prompt.
     * Results are returned in the same order as the input questions.
     *
     * @param courseId the id of the course the quiz belongs to
     * @param questions the multiple-choice questions to refine
     * @param refinementPrompt user instructions describing how all questions should change
     * @returns an observable that emits a map from each successfully refined question to its reasoning string; failed questions are omitted
     */
    refineAllMultipleChoiceQuestions(courseId: number, questions: MultipleChoiceQuestion[], refinementPrompt: string): Observable<Map<MultipleChoiceQuestion, string>> {
        const request: QuizQuestionBulkRefinementRequest = {
            questions: questions.map((q) => ({
                type: (q.singleChoice ? 'single-choice' : 'multiple-choice') as GeneratedQuizQuestion.TypeEnum,
                title: q.title?.trim() || 'Untitled Question',
                questionText: q.text ?? '',
                hint: q.hint ?? undefined,
                explanation: q.explanation ?? undefined,
                options: (q.answerOptions ?? []).map((opt) => ({
                    text: opt.text ?? '',
                    correct: !!opt.isCorrect,
                    hint: opt.hint ?? undefined,
                    explanation: opt.explanation ?? undefined,
                })),
            })),
            refinementPrompt,
        };
        return this.hyperionQuizQuestionGenerationApiService.refineAllQuizQuestions(courseId, request).pipe(
            map((response) => {
                const results = new Map<MultipleChoiceQuestion, string>();
                response.refinements.forEach((refinement, index) => {
                    if (refinement.type === QuizQuestionRefinementSuccessDTO.TypeEnum.Success) {
                        this.applyRefinedContentToQuestion(questions[index], this.toGeneratedQuestion(refinement.question, index));
                        results.set(questions[index], refinement.reasoning);
                    }
                });
                return results;
            }),
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
