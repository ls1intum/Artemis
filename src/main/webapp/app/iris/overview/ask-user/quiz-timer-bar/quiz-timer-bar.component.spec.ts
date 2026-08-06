import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import dayjs from 'dayjs/esm';

import { QuizTimerBarComponent } from 'app/iris/overview/ask-user/quiz-timer-bar/quiz-timer-bar.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

describe('QuizTimerBarComponent', () => {
    let fixture: ComponentFixture<QuizTimerBarComponent>;
    let component: QuizTimerBarComponent;

    beforeEach(async () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date('2026-01-01T00:00:00Z'));

        await TestBed.configureTestingModule({
            imports: [QuizTimerBarComponent],
        })
            .overrideComponent(QuizTimerBarComponent, {
                remove: { imports: [FaIconComponent, ArtemisTranslatePipe] },
                add: { imports: [MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe, (key: string) => key)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(QuizTimerBarComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('should display the configured time limit before a timer expiry is available', () => {
        fixture.componentRef.setInput('timeLimit', 45);
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('45 artemisApp.exerciseChatbot.secondsLeft');
        expect((fixture.nativeElement.querySelector('.quiz-timer-progress-fill') as HTMLElement).style.width).toBe('100%');
    });

    it('should render the compact open state without a progress bar', () => {
        fixture.componentRef.setInput('open', true);
        fixture.componentRef.setInput('showProgress', false);
        fixture.detectChanges();

        const timerBar = fixture.nativeElement.querySelector('.quiz-timer-bar') as HTMLElement;
        expect(timerBar.classList.contains('open')).toBe(true);
        expect(timerBar.classList.contains('compact')).toBe(true);
        expect(fixture.nativeElement.querySelector('.quiz-timer-progress')).toBeNull();
    });

    it('should update the displayed remaining time and progress while the timer is running', async () => {
        fixture.componentRef.setInput('timeLimit', 65);
        fixture.componentRef.setInput('timerExpiresAt', dayjs().add(65, 'seconds'));
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('1:05 artemisApp.exerciseChatbot.timeLeft');

        await vi.advanceTimersByTimeAsync(5000);
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('1:00 artemisApp.exerciseChatbot.timeLeft');
        expect(Number.parseFloat((fixture.nativeElement.querySelector('.quiz-timer-progress-fill') as HTMLElement).style.width)).toBeLessThan(100);
    });

    it('should emit once when the timer expires', async () => {
        const emitSpy = vi.spyOn(component.timerExpired, 'emit');

        fixture.componentRef.setInput('timeLimit', 1);
        fixture.componentRef.setInput('timerExpiresAt', dayjs().add(1, 'seconds'));
        fixture.detectChanges();

        await vi.advanceTimersByTimeAsync(1100);
        fixture.detectChanges();

        expect(emitSpy).toHaveBeenCalledOnce();
    });
});
