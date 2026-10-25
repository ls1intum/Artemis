import { ApplicationRef } from '@angular/core';
import { By } from '@angular/platform-browser';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { QuestionReevaluationActionsComponent } from './question-reevaluation-actions.component';

describe('question reevaluation actions', () => {
    beforeEach(() => TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] }));
    it('keeps the three independently addressable reorder and reset actions', () => {
        const fixture = TestBed.createComponent(QuestionReevaluationActionsComponent);
        const up = vi.fn();
        const down = vi.fn();
        const reset = vi.fn();
        fixture.componentInstance.moveUp.subscribe(up);
        fixture.componentInstance.moveDown.subscribe(down);
        fixture.componentInstance.resetQuestion.subscribe(reset);
        fixture.detectChanges();
        fixture.nativeElement.querySelector('#move-up-button').click();
        fixture.nativeElement.querySelector('#move-down-button').click();
        fixture.nativeElement.querySelector('#reset-button').click();
        expect(up).toHaveBeenCalledOnce();
        expect(down).toHaveBeenCalledOnce();
        expect(reset).toHaveBeenCalledOnce();
    });
    it('names each icon action and exposes its explanation to keyboard users', () => {
        const fixture = TestBed.createComponent(QuestionReevaluationActionsComponent);
        fixture.detectChanges();
        const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
        expect(buttons.map((button) => button.getAttribute('aria-label'))).toEqual([
            'artemisApp.quizExercise.re-evaluate.moveUp',
            'artemisApp.quizExercise.re-evaluate.moveDown',
            'artemisApp.quizExercise.re-evaluate.resetQuestion',
        ]);
        const styledButtons = fixture.debugElement.queryAll(By.directive(TumAetUiButtonDirective));
        expect(styledButtons).toHaveLength(3);
        for (const button of styledButtons) {
            const appearance = button.injector.get(TumAetUiButtonDirective);
            expect(appearance.severity()).toBe('secondary');
            expect(appearance.variant()).toBe('outlined');
        }
        vi.useFakeTimers();
        try {
            buttons[0].dispatchEvent(new Event('focusin', { bubbles: true }));
            vi.advanceTimersByTime(150);
            TestBed.inject(ApplicationRef).tick();
            const tooltipId = buttons[0].getAttribute('aria-describedby');
            expect(tooltipId).toBeTruthy();
            expect(document.getElementById(tooltipId!)?.textContent).toContain('artemisApp.quizExercise.re-evaluate.moveUp');
            buttons[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
            expect(buttons[0].hasAttribute('aria-describedby')).toBe(false);
            expect(document.getElementById(tooltipId!)).toBeNull();
        } finally {
            fixture.destroy();
            vi.useRealTimers();
        }
    });
});
