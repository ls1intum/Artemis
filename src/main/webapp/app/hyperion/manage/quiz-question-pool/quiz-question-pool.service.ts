import { Service, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { HyperionQuizQuestionPoolApi } from 'app/openapi/api/hyperion-quiz-question-pool-api';
import { QuizQuestionPoolItem } from 'app/openapi/model/quiz-question-pool-item';
import { QuizQuestionPoolProgress } from 'app/openapi/model/quiz-question-pool-progress';
import { HyperionCourseSettings } from 'app/openapi/model/hyperion-course-settings';
import { GeneratedQuestion } from 'app/quiz/manage/update/quiz-ai-generation-modal/quiz-ai-generation.types';
import { PoolBuildStart, PoolProgress, PoolQuestion, PoolReviewState, PoolSettings } from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool.types';

@Service()
export class QuizQuestionPoolService {
    private api = inject(HyperionQuizQuestionPoolApi);

    /**
     * Loads how far the course's pool has come.
     *
     * @param courseId the course
     */
    getProgress(courseId: number): Observable<PoolProgress> {
        return this.api.getQuizQuestionPoolProgress(courseId).pipe(map((progress) => this.toProgress(progress)));
    }

    /**
     * Loads the pool questions of the course with their filter decisions and lecture units, in cell order.
     *
     * @param courseId     the course
     * @param competencyId restricts the result to one competency when given
     */
    getQuestions(courseId: number, competencyId?: number): Observable<PoolQuestion[]> {
        return this.api.getQuizQuestionPoolQuestions(courseId, competencyId).pipe(map((items) => items.map((item) => this.toPoolQuestion(item))));
    }

    /**
     * Tops the pool up and starts a build in the background. A build already running on the server is reported as not started
     * rather than as an error.
     *
     * @param courseId the course
     */
    startBuild(courseId: number): Observable<PoolBuildStart> {
        return this.api.startQuizQuestionPoolBuild(courseId).pipe(
            map((result) => ({ started: !!result.started, enqueued: result.enqueued ?? 0 })),
            catchError((error: HttpErrorResponse) => (error.status === 409 ? of({ started: false, enqueued: 0 }) : throwError(() => error))),
        );
    }

    /**
     * Loads the course's Hyperion settings.
     *
     * @param courseId the course
     */
    getSettings(courseId: number): Observable<PoolSettings> {
        return this.api.getQuizQuestionPoolSettings(courseId).pipe(map((settings) => this.toSettings(settings)));
    }

    /**
     * Replaces the course's Hyperion settings.
     *
     * @param courseId the course
     * @param settings the settings to store
     */
    updateSettings(courseId: number, settings: PoolSettings): Observable<PoolSettings> {
        return this.api.updateQuizQuestionPoolSettings(courseId, settings).pipe(map((stored) => this.toSettings(stored)));
    }

    /**
     * Records the review decision on one pool question.
     *
     * @param courseId    the course
     * @param questionId  the pool question
     * @param reviewState the decision
     */
    review(courseId: number, questionId: number, reviewState: PoolReviewState): Observable<PoolQuestion> {
        return this.api.updateQuizQuestionPoolReview(courseId, questionId, { reviewState }).pipe(map((item) => this.toPoolQuestion(item)));
    }

    private toProgress(progress: QuizQuestionPoolProgress): PoolProgress {
        return {
            running: !!progress.running,
            total: progress.total ?? 0,
            toGenerate: progress.toGenerate ?? 0,
            toJudge: progress.toJudge ?? 0,
            judged: progress.judged ?? 0,
            accepted: progress.accepted ?? 0,
            rejected: progress.rejected ?? 0,
            failed: progress.failed ?? 0,
        };
    }

    private toSettings(settings: HyperionCourseSettings): PoolSettings {
        return { poolGenerationEnabled: !!settings.poolGenerationEnabled, questionsPerLectureUnit: settings.questionsPerLectureUnit };
    }

    private toPoolQuestion(item: QuizQuestionPoolItem): PoolQuestion {
        return {
            id: item.id,
            competencyId: item.competencyId,
            competencyTitle: item.competencyTitle ?? '',
            lectureUnitId: item.lectureUnitId,
            lectureUnitName: item.lectureUnitName ?? '',
            language: item.language,
            questionType: item.questionType,
            difficulty: item.difficulty,
            state: item.state,
            reviewState: item.reviewState,
            question: item.question ? this.toGeneratedQuestion(item.question, item.id) : undefined,
            decision: item.decision
                ? {
                      accepted: !!item.decision.accepted,
                      aggregateScore: item.decision.aggregateScore ?? 0,
                      verdicts: (item.decision.verdicts ?? []).map((verdict) => ({
                          mode: verdict.mode,
                          severity: verdict.severity ?? 0,
                          triggered: !!verdict.triggered,
                          justification: verdict.justification ?? undefined,
                      })),
                      rationale: item.decision.rationale ?? undefined,
                  }
                : undefined,
            failure: item.failure ?? undefined,
            generatorModel: item.generatorModel ?? undefined,
            judgeModel: item.judgeModel ?? undefined,
            updatedAt: item.updatedAt,
        };
    }

    private toGeneratedQuestion(question: Omit<GeneratedQuestion, 'id'>, itemId: number): GeneratedQuestion {
        return {
            id: `pool-${itemId}`,
            type: question.type,
            title: question.title,
            questionText: question.questionText,
            hint: question.hint ?? undefined,
            explanation: question.explanation ?? undefined,
            options: (question.options ?? []).map((option) => ({
                text: option.text,
                correct: !!option.correct,
                hint: option.hint ?? undefined,
                explanation: option.explanation ?? undefined,
            })),
        };
    }
}
