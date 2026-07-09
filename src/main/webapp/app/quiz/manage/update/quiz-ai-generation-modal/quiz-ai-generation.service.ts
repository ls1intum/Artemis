import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { map } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { HyperionQuizQuestionGenerationApi } from 'app/openapi/api/hyperion-quiz-question-generation-api';
import { QuizQuestionGenerationRequest } from 'app/openapi/models/quiz-question-generation-request';
import { QuizQuestionBulkRefinementRequest } from 'app/openapi/models/quiz-question-bulk-refinement-request';
import { QuizQuestionRefinementResponse } from 'app/openapi/models/quiz-question-refinement-response';
import {
    GeneratedQuestion,
    GeneratedQuestionType,
    QuizQuestionBulkRefinementResult,
    QuizQuestionRefinementResult,
} from 'app/quiz/manage/update/quiz-ai-generation-modal/quiz-ai-generation.types';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { deepClone } from 'app/foundation/util/deep-clone.util';

@Injectable({ providedIn: 'root' })
export class QuizAiGenerationService {
    private hyperionQuizQuestionGenerationApiService = inject(HyperionQuizQuestionGenerationApi);
    private translateService = inject(TranslateService);
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
     * @returns an observable that emits the refined question, the AI reasoning string, and a previousQuestion snapshot taken before refinement for restore capability
     */
    refineMultipleChoiceQuestion(courseId: number, question: MultipleChoiceQuestion, refinementPrompt: string): Observable<QuizQuestionRefinementResult> {
        const request = {
            question: {
                type: (question.singleChoice ? 'single-choice' : 'multiple-choice') as GeneratedQuestionType,
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
            map((response: QuizQuestionRefinementResponse) => {
                if (response.type === 'success') {
                    // The generated response type models the refined question polymorphically and omits `question`,
                    // but a successful refinement always carries it at runtime; narrow to the shape that includes it.
                    const success = response as QuizQuestionRefinementResponse & { question: Omit<GeneratedQuestion, 'id'> };
                    const previousQuestion = deepClone(question);
                    return {
                        refinedQuestion: this.applyRefinedContentToQuestion(question, this.toGeneratedQuestion(success.question, 0)),
                        reasoning: response.reasoning,
                        previousQuestion,
                    };
                }
                throw new Error(this.translateService.instant('artemisApp.quizExercise.aiGeneration.refinement.errors.failed'));
            }),
        );
    }

    /**
     * Sends all provided multiple-choice questions to Hyperion for bulk AI-driven refinement using one shared prompt.
     * Results are returned in the same order as the input questions.
     *
     * @param courseId the id of the course the quiz belongs to
     * @param questions the multiple-choice questions to refine
     * @param refinementPrompt user instructions describing how all questions should change
     * @returns an observable that emits an object with a results map (each successfully refined question to its reasoning string; failed questions omitted) and a previousSnapshots map (each refined question to its pre-refinement snapshot for restore capability)
     */
    refineAllMultipleChoiceQuestions(courseId: number, questions: MultipleChoiceQuestion[], refinementPrompt: string): Observable<QuizQuestionBulkRefinementResult> {
        const request: QuizQuestionBulkRefinementRequest = {
            questions: questions.map((q) => ({
                type: q.singleChoice ? 'single-choice' : 'multiple-choice',
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
                const previousSnapshots = new Map<MultipleChoiceQuestion, MultipleChoiceQuestion>();
                response.refinements.forEach((refinement, index) => {
                    if (refinement.type === 'success') {
                        // See note above: `question` is present at runtime for successful refinements but not in the generated type.
                        const success = refinement as typeof refinement & { question: Omit<GeneratedQuestion, 'id'> };
                        previousSnapshots.set(questions[index], deepClone(questions[index]));
                        this.applyRefinedContentToQuestion(questions[index], this.toGeneratedQuestion(success.question, index));
                        results.set(questions[index], refinement.reasoning);
                    }
                });
                return { results, previousSnapshots };
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

    private toGeneratedQuestion(question: Omit<GeneratedQuestion, 'id'>, index: number): GeneratedQuestion {
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
