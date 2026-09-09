import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { QuizExerciseCountdownComponent } from 'app/exercise/exercise-headers/quiz-countdown/quiz-exercise-countdown.component';

describe('QuizExerciseCountdownComponent', () => {
    let fixture: ComponentFixture<QuizExerciseCountdownComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [QuizExerciseCountdownComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(QuizExerciseCountdownComponent);
    });

    function textOf(testId: string): string | undefined {
        return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`)?.textContent?.trim();
    }

    it('should show the remaining time while the quiz is running', () => {
        fixture.componentRef.setInput('info', { showRemainingTime: true, remainingTimeText: '12 min 30 s', showResultsAvailable: false });
        fixture.detectChanges();

        expect(textOf('quiz-countdown-value')).toBe('12 min 30 s');
    });

    it('should mark the remaining time critical when a colour is given', () => {
        fixture.componentRef.setInput('info', {
            showRemainingTime: true,
            remainingTimeText: '9 s',
            remainingTimeColor: 'danger',
            showResultsAvailable: false,
        });
        fixture.detectChanges();

        // On the box, not the number: the border and background carry the urgency too.
        expect(fixture.nativeElement.querySelector('[data-testid="quiz-countdown"]').getAttribute('data-severity')).toBe('danger');
    });

    it('should show the duration before the quiz starts', () => {
        fixture.componentRef.setInput('info', { showRemainingTime: false, showDuration: true, durationText: '45min', showResultsAvailable: false });
        fixture.detectChanges();

        expect(textOf('quiz-countdown-value')).toBe('45min');
    });

    it('should render nothing once neither time nor duration applies', () => {
        fixture.componentRef.setInput('info', { showRemainingTime: false, showResultsAvailable: true });
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="quiz-countdown-value"]')).toBeNull();
    });

    it('should render nothing without info', () => {
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="quiz-countdown-value"]')).toBeNull();
    });

    it('should announce the remaining time politely', () => {
        fixture.componentRef.setInput('info', { showRemainingTime: true, remainingTimeText: '1 min', showResultsAvailable: false });
        fixture.detectChanges();

        const host = fixture.nativeElement.querySelector('[data-testid="quiz-countdown"]');
        expect(host.getAttribute('role')).toBe('timer');
        expect(host.getAttribute('aria-live')).toBe('polite');
    });
});
