import { describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { QuizQuestionPoolItemListComponent } from './quiz-question-pool-item-list.component';
import { PoolFilterVerdict, PoolItemState, PoolQuestion, PoolReviewState } from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool.types';

describe('QuizQuestionPoolItemListComponent', () => {
    let fixture: ComponentFixture<QuizQuestionPoolItemListComponent>;
    let component: QuizQuestionPoolItemListComponent;

    function verdicts(overrides: Partial<PoolFilterVerdict>[] = []): PoolFilterVerdict[] {
        const all: PoolFilterVerdict[] = (['FACTUAL_ERROR', 'AMBIGUOUS_CORRECT_ANSWER', 'OFF_TOPIC', 'NEAR_DUPLICATE', 'ILL_FORMED_DISTRACTORS'] as const).map((mode) => ({
            mode,
            severity: 0,
            triggered: false,
            justification: `${mode} is fine`,
            flagged: false,
        }));
        for (const override of overrides) {
            Object.assign(
                all.find((verdict) => verdict.mode === override.mode)!,
                override,
            );
        }
        return all;
    }

    function question(
        id: number,
        options: { state?: PoolItemState; accepted?: boolean; flagged?: Partial<PoolFilterVerdict>[]; reviewState?: PoolReviewState } = {},
    ): PoolQuestion {
        const state = options.state ?? 'FILTERED';
        const judged = state === 'FILTERED';
        const allVerdicts = verdicts(options.flagged ?? []);
        return {
            id,
            competencyId: 9,
            competencyTitle: 'Arrays',
            lectureUnitId: 4,
            lectureUnitName: 'Arrays in Java',
            language: 'en',
            questionType: 'single-choice',
            difficulty: 'MEDIUM',
            state,
            reviewState: options.reviewState ?? 'PENDING',
            flagged: judged ? allVerdicts.some((verdict) => verdict.flagged) : undefined,
            question: judged
                ? {
                      id: `pool-${id}`,
                      type: 'single-choice',
                      title: `Question ${id}`,
                      questionText: `What about ${id}?`,
                      options: [
                          { text: 'A', correct: true },
                          { text: 'B', correct: false },
                      ],
                  }
                : undefined,
            decision: judged
                ? { accepted: options.accepted ?? true, aggregateScore: 1 - Math.max(...allVerdicts.map((v) => v.severity)), verdicts: allVerdicts, rationale: 'checked' }
                : undefined,
            updatedAt: '2026-10-13T10:00:00Z',
        };
    }

    function setup(questions: PoolQuestion[], canReview = true): void {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(QuizQuestionPoolItemListComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('questions', questions);
        fixture.componentRef.setInput('canReview', canReview);
        fixture.detectChanges();
    }

    const defaultQuestions = (): PoolQuestion[] => [
        question(1),
        question(2, { flagged: [{ mode: 'ILL_FORMED_DISTRACTORS', severity: 0.5, flagged: true }] }),
        question(3, { accepted: false, flagged: [{ mode: 'FACTUAL_ERROR', severity: 1, triggered: true, flagged: true }] }),
        question(4, { state: 'PENDING' }),
        question(5, { state: 'FAILED_GENERATION' }),
    ];

    it('should split judged questions into needs review, accepted and rejected', () => {
        setup(defaultQuestions());

        expect(component.needsReview().map((q) => q.id)).toEqual([2]);
        expect(component.accepted().map((q) => q.id)).toEqual([1]);
        expect(component.rejected().map((q) => q.id)).toEqual([3]);
        expect(fixture.debugElement.query(By.css('[data-testid="pool-section-needs-review"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="pool-section-accepted"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="pool-section-rejected"]'))).not.toBeNull();
    });

    it('should count questions that are not judged yet and questions that failed', () => {
        setup(defaultQuestions());

        expect(component.inProgressCount()).toBe(1);
        expect(component.failedCount()).toBe(1);
        expect(fixture.debugElement.query(By.css('[data-testid="pool-list-in-progress"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="pool-list-failed"]'))).not.toBeNull();
    });

    it('should show the empty note when there is nothing to list', () => {
        setup([]);

        expect(fixture.debugElement.query(By.css('[data-testid="pool-list-empty"]'))).not.toBeNull();
    });

    it('should number questions across the sections', () => {
        setup(defaultQuestions());

        expect(component.needsReviewViews().map((item) => [item.question.id, item.index])).toEqual([[2, 0]]);
        expect(component.acceptedViews().map((item) => [item.question.id, item.index])).toEqual([[1, 1]]);
        expect(component.rejectedViews().map((item) => [item.question.id, item.index])).toEqual([[3, 2]]);
    });

    it('should list only flagged verdicts as badges, worst first', () => {
        setup([
            question(7, {
                flagged: [
                    { mode: 'OFF_TOPIC', severity: 0.3, flagged: true },
                    { mode: 'FACTUAL_ERROR', severity: 0.5, flagged: true },
                    { mode: 'NEAR_DUPLICATE', severity: 0.9, triggered: true },
                ],
            }),
        ]);

        const [item] = component.needsReviewViews();
        expect(item.flaggedVerdicts.map((flagged) => flagged.verdict.mode)).toEqual(['FACTUAL_ERROR', 'OFF_TOPIC']);
        expect(item.flaggedVerdicts.map((flagged) => flagged.percent)).toEqual([50, 30]);
        expect(item.verdicts).toHaveLength(5);
        expect(item.scorePercent).toBe(10);
    });

    it('should follow the server flags and keep unflagged verdicts in the rationale only', () => {
        setup([
            question(1, { flagged: [{ mode: 'NEAR_DUPLICATE', severity: 0.9, triggered: true }] }),
            question(2, { flagged: [{ mode: 'ILL_FORMED_DISTRACTORS', severity: 0.2 }] }),
            question(3, { flagged: [{ mode: 'ILL_FORMED_DISTRACTORS', severity: 0.4, flagged: true }] }),
        ]);

        expect(component.needsReviewViews().map((item) => item.question.id)).toEqual([3]);
        expect(component.acceptedViews().map((item) => item.question.id)).toEqual([1, 2]);
        expect(component.acceptedViews().map((item) => item.flaggedVerdicts.length)).toEqual([0, 0]);
        expect(component.acceptedViews()[0].verdicts.find((entry) => entry.verdict.mode === 'NEAR_DUPLICATE')?.percent).toBe(90);
    });

    it('should toggle the rationale of a question', () => {
        setup(defaultQuestions());

        expect(fixture.debugElement.query(By.css('[data-testid="pool-rationale-2"]'))).toBeNull();
        fixture.debugElement.queryAll(By.css('[data-testid="pool-item-2"] button'))[0].nativeElement.click();
        fixture.detectChanges();
        expect(component.needsReviewViews()[0].expanded).toBe(true);
        expect(fixture.debugElement.query(By.css('[data-testid="pool-rationale-2"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="pool-verdict-2-ILL_FORMED_DISTRACTORS"]'))).not.toBeNull();
        component.toggleRationale(2);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('[data-testid="pool-rationale-2"]'))).toBeNull();
    });

    it('should emit from the buttons and hide them without review rights', () => {
        setup([question(1), question(2, { reviewState: 'APPROVED' })]);
        const emitted = vi.fn();
        component.review.subscribe(emitted);

        fixture.debugElement.query(By.css('[data-testid="pool-approve-1"]')).nativeElement.click();
        expect(emitted).toHaveBeenCalledWith({ questionId: 1, reviewState: 'APPROVED' });
        fixture.debugElement.query(By.css('[data-testid="pool-approve-2"]')).nativeElement.click();
        expect(emitted).toHaveBeenCalledWith({ questionId: 2, reviewState: 'PENDING' });
        fixture.debugElement.query(By.css('[data-testid="pool-exclude-2"]')).nativeElement.click();
        expect(emitted).toHaveBeenCalledWith({ questionId: 2, reviewState: 'EXCLUDED' });

        fixture.componentRef.setInput('canReview', false);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('[data-testid="pool-approve-1"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="pool-review-state-1"]'))).not.toBeNull();
    });

    it('should disable the buttons of a question whose decision is being saved', () => {
        setup(defaultQuestions());
        fixture.componentRef.setInput('busyQuestionIds', new Set([1]));
        fixture.detectChanges();

        expect(component.acceptedViews()[0].busy).toBe(true);
        expect(fixture.debugElement.query(By.css('[data-testid="pool-approve-1"]')).nativeElement.disabled).toBe(true);
        expect(fixture.debugElement.query(By.css('[data-testid="pool-exclude-2"]')).nativeElement.disabled).toBe(false);
    });

    it('should map review states to tag severities and labels', () => {
        setup([question(1, { reviewState: 'APPROVED' }), question(2, { reviewState: 'EXCLUDED' }), question(3)]);

        const views = component.acceptedViews();
        expect(views.map((item) => item.reviewSeverity)).toEqual(['success', 'contrast', 'secondary']);
        expect(views.map((item) => item.reviewLabelKey)).toEqual([
            'artemisApp.hyperion.quizQuestionPool.list.reviewState.APPROVED',
            'artemisApp.hyperion.quizQuestionPool.list.reviewState.EXCLUDED',
            'artemisApp.hyperion.quizQuestionPool.list.reviewState.PENDING',
        ]);
    });
});
