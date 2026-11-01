import { Service, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { HyperionQuizQuestionPoolApi } from 'app/openapi/api/hyperion-quiz-question-pool-api';
import { GeneratedQuizQuestion } from 'app/openapi/model/generated-quiz-question';
import { QuizQuestionPoolItem } from 'app/openapi/model/quiz-question-pool-item';
import { QuizQuestionPoolProgress } from 'app/openapi/model/quiz-question-pool-progress';
import { HyperionCourseSettings } from 'app/openapi/model/hyperion-course-settings';
import { GeneratedQuestion } from 'app/hyperion/quiz/quiz-ai-generation-modal/quiz-ai-generation.types';
import {
    PoolBuildStart,
    PoolProgress,
    PoolQuestion,
    PoolQuestionPage,
    PoolQuestionQuery,
    PoolReviewState,
    PoolSettings,
} from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool.types';

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
     * Loads one page of the pool's questions in pool order, optionally only those of one competency or only those flagged for
     * review, the undecided ones first.
     *
     * @param courseId the course
     * @param query    the competency filter, the review filter and the page to load
     * @returns the page with its items, the matching total and the competencies that have questions
     */
    getQuestions(courseId: number, query: PoolQuestionQuery): Observable<PoolQuestionPage> {
        return this.api.getQuizQuestionPoolQuestions(courseId, query.competencyId, query.flagged ?? false, query.page, query.size).pipe(
            map((page) => ({
                items: (page.items ?? []).map((item) => this.toPoolQuestion(item)),
                totalCount: page.totalCount,
                competencies: (page.competencies ?? []).map((competency) => ({ id: competency.id, title: competency.title ?? '' })),
            })),
        );
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
     * Asks the running build to stop after its current step; the build drops the questions it has not generated yet. A 409 means
     * that no build is running.
     *
     * @param courseId the course
     */
    cancelBuild(courseId: number): Observable<void> {
        return this.api.cancelQuizQuestionPoolBuild(courseId).pipe(map(() => undefined));
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

    /**
     * Removes one question from the pool; the next build generates a new one for its slot.
     *
     * @param courseId   the course
     * @param questionId the pool item
     */
    deleteQuestion(courseId: number, questionId: number): Observable<void> {
        return this.api.deleteQuizQuestionPoolQuestion(courseId, questionId).pipe(map(() => undefined));
    }

    /**
     * Removes every question of the pool, or of one competency; the next build generates them anew.
     *
     * @param courseId     the course
     * @param competencyId the competency to clear, or undefined for the whole pool
     * @returns how many questions were removed
     */
    deleteQuestions(courseId: number, competencyId?: number): Observable<number> {
        return this.api.deleteQuizQuestionPoolQuestions(courseId, competencyId).pipe(map((result) => result.deleted));
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
            buildSettings: progress.buildSettings ? this.toSettings(progress.buildSettings) : undefined,
        };
    }

    private toSettings(settings: HyperionCourseSettings): PoolSettings {
        return {
            poolGenerationEnabled: settings.poolGenerationEnabled,
            singleChoiceQuestionsPerLectureUnit: settings.singleChoiceQuestionsPerLectureUnit,
            multipleChoiceEnabled: settings.multipleChoiceEnabled,
            multipleChoiceQuestionsPerLectureUnit: settings.multipleChoiceQuestionsPerLectureUnit,
            multipleChoiceMinCorrectOptions: settings.multipleChoiceMinCorrectOptions,
            multipleChoiceMaxCorrectOptions: settings.multipleChoiceMaxCorrectOptions,
            easyPercent: settings.easyPercent,
            mediumPercent: settings.mediumPercent,
            hardPercent: settings.hardPercent,
        };
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
            flagged: item.flagged ?? undefined,
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
                          flagged: !!verdict.flagged,
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

    private toGeneratedQuestion(question: GeneratedQuizQuestion, itemId: number): GeneratedQuestion {
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
