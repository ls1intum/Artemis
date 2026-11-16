import { describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { QuizQuestionPoolItemComponent } from './quiz-question-pool-item.component';
import { PoolQuestionView, PoolReviewState } from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool.types';

describe('QuizQuestionPoolItemComponent', () => {
    let fixture: ComponentFixture<QuizQuestionPoolItemComponent>;
    let component: QuizQuestionPoolItemComponent;

    function view(reviewState: PoolReviewState = 'PENDING', triggered = false): PoolQuestionView {
        const verdict = { mode: 'ILL_FORMED_DISTRACTORS' as const, severity: 0.4, triggered, justification: 'options differ in length', flagged: true };
        return {
            question: {
                id: 7,
                competencyId: 9,
                competencyTitle: 'Arrays',
                lectureUnitId: 4,
                lectureUnitName: 'Arrays in Java',
                language: 'en',
                questionType: 'single-choice',
                difficulty: 'MEDIUM',
                state: 'FILTERED',
                reviewState,
                flagged: true,
                question: { id: 'pool-7', type: 'single-choice', title: 'Q7', questionText: 'What?', options: [{ text: 'A', correct: true }] },
                decision: { accepted: true, aggregateScore: 0.6, verdicts: [verdict], rationale: 'checked' },
                updatedAt: '2026-10-13T10:00:00Z',
            },
            index: 2,
            flaggedVerdicts: [{ verdict, labelKey: 'artemisApp.hyperion.quizQuestionPool.list.failureModes.ILL_FORMED_DISTRACTORS', percent: 40 }],
            verdicts: [{ verdict, labelKey: 'artemisApp.hyperion.quizQuestionPool.list.failureModes.ILL_FORMED_DISTRACTORS', percent: 40 }],
            scorePercent: 60,
            expanded: false,
            busy: false,
            reviewSeverity: 'secondary',
            reviewLabelKey: 'artemisApp.hyperion.quizQuestionPool.list.reviewState.PENDING',
        };
    }

    function setup(item: PoolQuestionView, canReview = true): void {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(QuizQuestionPoolItemComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('item', item);
        fixture.componentRef.setInput('canReview', canReview);
        fixture.detectChanges();
    }

    it('should emit the decision and return to pending when the same decision is taken again', () => {
        setup(view('APPROVED'));
        const emitted = vi.fn();
        component.review.subscribe(emitted);

        fixture.debugElement.query(By.css('[data-testid="pool-approve-7"]')).nativeElement.click();
        fixture.debugElement.query(By.css('[data-testid="pool-exclude-7"]')).nativeElement.click();

        expect(emitted).toHaveBeenNthCalledWith(1, { questionId: 7, reviewState: 'PENDING' });
        expect(emitted).toHaveBeenNthCalledWith(2, { questionId: 7, reviewState: 'EXCLUDED' });
    });

    it('should mark a triggered verdict with an icon and a text cue', () => {
        setup(view('PENDING', true));

        const tag = fixture.debugElement.query(By.css('[data-testid="pool-verdict-7-ILL_FORMED_DISTRACTORS"]'));
        expect(tag.nativeElement.querySelector('fa-icon')).not.toBeNull();
        expect(tag.nativeElement.querySelector('.sr-only')).not.toBeNull();
    });

    it('should offer deletion only to instructors and emit the question id', () => {
        setup(view());
        expect(fixture.debugElement.query(By.css('[data-testid="pool-delete-7"]'))).toBeNull();
        const deleted = vi.fn();
        component.delete.subscribe(deleted);

        fixture.componentRef.setInput('canDelete', true);
        fixture.detectChanges();
        fixture.debugElement.query(By.css('[data-testid="pool-delete-7"]')).nativeElement.click();

        expect(deleted).toHaveBeenCalledWith(7);

        fixture.componentRef.setInput('deletionBlocked', true);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('[data-testid="pool-delete-7"]')).nativeElement.disabled).toBe(true);
    });

    it('should show the competency and lecture unit on request', () => {
        setup(view());
        expect(fixture.debugElement.query(By.css('[data-testid="pool-item-context-7"]'))).toBeNull();

        fixture.componentRef.setInput('showContext', true);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('[data-testid="pool-item-context-7"]')).nativeElement.textContent).toContain('Arrays');
    });

    it('should hide the review buttons without review rights and ask to expand the rationale', () => {
        setup(view(), false);
        const toggled = vi.fn();
        component.toggleRationale.subscribe(toggled);

        expect(fixture.debugElement.query(By.css('[data-testid="pool-approve-7"]'))).toBeNull();
        fixture.debugElement.query(By.css('button')).nativeElement.click();

        expect(toggled).toHaveBeenCalledWith(7);
    });
});
