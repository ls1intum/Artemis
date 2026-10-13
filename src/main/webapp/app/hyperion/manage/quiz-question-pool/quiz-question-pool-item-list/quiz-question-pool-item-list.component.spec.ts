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
            language: 'en',
            questionType: 'single-choice',
            difficulty: 'MEDIUM',
            state,
            reviewState: options.reviewState ?? 'PENDING',
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
        question(2, { flagged: [{ mode: 'NEAR_DUPLICATE', severity: 0.5, triggered: true }] }),
        question(3, { accepted: false, flagged: [{ mode: 'FACTUAL_ERROR', severity: 1, triggered: true }] }),
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

        expect(fixture.debugElement.query(By.css('.pool-list-empty'))).not.toBeNull();
    });

    it('should number questions across the sections', () => {
        setup(defaultQuestions());

        expect(component.displayIndex(2)).toBe(0);
        expect(component.displayIndex(1)).toBe(1);
        expect(component.displayIndex(3)).toBe(2);
    });

    it('should list only flagged verdicts as badges, worst first', () => {
        setup(defaultQuestions());
        const decision = question(7, {
            flagged: [
                { mode: 'OFF_TOPIC', severity: 0.3 },
                { mode: 'FACTUAL_ERROR', severity: 0.5 },
                { mode: 'NEAR_DUPLICATE', triggered: true },
            ],
        }).decision!;

        expect(component.flaggedVerdicts(decision).map((v) => v.mode)).toEqual(['FACTUAL_ERROR', 'OFF_TOPIC', 'NEAR_DUPLICATE']);
    });

    it('should toggle the rationale of a question', () => {
        setup(defaultQuestions());

        expect(fixture.debugElement.query(By.css('[data-testid="pool-rationale-2"]'))).toBeNull();
        component.toggleRationale(2);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('[data-testid="pool-rationale-2"]'))).not.toBeNull();
        component.toggleRationale(2);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('[data-testid="pool-rationale-2"]'))).toBeNull();
    });

    it('should emit the review decision and return to pending when the same decision is taken again', () => {
        setup(defaultQuestions());
        const emitted = vi.fn();
        component.review.subscribe(emitted);

        component.setReview(question(2), 'APPROVED');
        component.setReview(question(2, { reviewState: 'APPROVED' }), 'APPROVED');
        component.setReview(question(3, { reviewState: 'APPROVED' }), 'EXCLUDED');

        expect(emitted).toHaveBeenNthCalledWith(1, { questionId: 2, reviewState: 'APPROVED' });
        expect(emitted).toHaveBeenNthCalledWith(2, { questionId: 2, reviewState: 'PENDING' });
        expect(emitted).toHaveBeenNthCalledWith(3, { questionId: 3, reviewState: 'EXCLUDED' });
    });

    it('should emit from the buttons and hide them without review rights', () => {
        setup(defaultQuestions());
        const emitted = vi.fn();
        component.review.subscribe(emitted);

        fixture.debugElement.query(By.css('[data-testid="pool-approve-1"]')).nativeElement.click();
        expect(emitted).toHaveBeenCalledWith({ questionId: 1, reviewState: 'APPROVED' });

        fixture.componentRef.setInput('canReview', false);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('[data-testid="pool-approve-1"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="pool-review-state-1"]'))).not.toBeNull();
    });

    it('should disable the buttons of a question whose decision is being saved', () => {
        setup(defaultQuestions());
        fixture.componentRef.setInput('busyQuestionIds', new Set([1]));
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('[data-testid="pool-approve-1"]')).nativeElement.disabled).toBe(true);
        expect(fixture.debugElement.query(By.css('[data-testid="pool-exclude-2"]')).nativeElement.disabled).toBe(false);
    });

    it('should map review states to tag severities and percentages', () => {
        setup([]);

        expect(component.getReviewStateSeverity('APPROVED')).toBe('success');
        expect(component.getReviewStateSeverity('EXCLUDED')).toBe('contrast');
        expect(component.getReviewStateSeverity('PENDING')).toBe('secondary');
        expect(component.asPercent(0.7)).toBe(70);
    });
});
