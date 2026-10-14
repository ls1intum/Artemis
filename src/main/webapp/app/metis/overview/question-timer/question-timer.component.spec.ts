import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockDirective, MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { QuestionTimerComponent } from 'app/metis/overview/question-timer/question-timer.component';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockArtemisServerDateService } from 'test/helpers/mocks/service/mock-server-date.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('QuestionTimerComponent', () => {
    const shownAt = dayjs('2026-11-03T10:15:00');
    const limitSeconds = 240;

    let fixture: ComponentFixture<QuestionTimerComponent>;
    let component: QuestionTimerComponent;
    let nowSpy: ReturnType<typeof vi.spyOn>;
    let now: dayjs.Dayjs;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [QuestionTimerComponent],
            providers: [
                { provide: ArtemisServerDateService, useClass: MockArtemisServerDateService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(QuestionTimerComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe] },
                add: { imports: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)] },
            })
            .compileComponents();

        // The server clock is a variable the tests move; the interval is a fake timer the tests advance.
        vi.useFakeTimers();
        now = shownAt;
        nowSpy = vi.spyOn(TestBed.inject(ArtemisServerDateService), 'now').mockImplementation(() => now);

        fixture = TestBed.createComponent(QuestionTimerComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('endDate', shownAt.add(limitSeconds, 'second'));
        fixture.componentRef.setInput('totalSeconds', limitSeconds);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    function display(): string {
        return fixture.nativeElement.querySelector('[data-testid="question-timer-display"]').textContent.trim();
    }

    function barValue(): string | null {
        return fixture.nativeElement.querySelector('tumaet-ui-progress-bar').getAttribute('aria-valuenow');
    }

    function isCritical(): boolean {
        return fixture.nativeElement.querySelector('[data-testid="question-timer"]').classList.contains('critical');
    }

    /** Moves the server clock to the given second after the question was shown and lets one tick run. */
    function advanceClockTo(secondsAfterShown: number): void {
        now = shownAt.add(secondsAfterShown, 'second');
        vi.advanceTimersByTime(250);
        fixture.detectChanges();
    }

    it('should show the whole limit as m:ss with a full bar', () => {
        expect(display()).toBe('4:00');
        expect(barValue()).toBe('100');
        expect(isCritical()).toBe(false);
    });

    it('should count down from the server clock on every tick', () => {
        advanceClockTo(90);

        expect(display()).toBe('2:30');
        expect(barValue()).toBe('63');
        expect(isCritical()).toBe(false);
    });

    it('should turn critical at 30 seconds', () => {
        advanceClockTo(limitSeconds - 30);

        expect(display()).toBe('0:30');
        expect(isCritical()).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-severity="warn"]')).not.toBeNull();
    });

    it('should emit expired exactly once and stop ticking afterwards', () => {
        const expiredSpy = vi.fn();
        component.expired.subscribe(expiredSpy);

        advanceClockTo(limitSeconds);

        expect(display()).toBe('0:00');
        expect(barValue()).toBe('0');
        expect(expiredSpy).toHaveBeenCalledOnce();

        nowSpy.mockClear();
        advanceClockTo(limitSeconds + 60);
        vi.advanceTimersByTime(5000);

        expect(expiredSpy).toHaveBeenCalledOnce();
        expect(nowSpy).not.toHaveBeenCalled();
    });

    it('should never show a negative remaining time for a limit that is already over', () => {
        const expiredSpy = vi.fn();
        component.expired.subscribe(expiredSpy);

        fixture.componentRef.setInput('endDate', shownAt.subtract(10, 'second'));
        fixture.detectChanges();

        expect(display()).toBe('0:00');
        expect(expiredSpy).toHaveBeenCalledOnce();
    });

    it('should restart the countdown for the next question', () => {
        const expiredSpy = vi.fn();
        component.expired.subscribe(expiredSpy);
        advanceClockTo(limitSeconds);
        expect(expiredSpy).toHaveBeenCalledOnce();

        fixture.componentRef.setInput('endDate', now.add(180, 'second'));
        fixture.componentRef.setInput('totalSeconds', 180);
        fixture.detectChanges();

        expect(display()).toBe('3:00');
        expect(barValue()).toBe('100');
        expect(isCritical()).toBe(false);

        now = now.add(180, 'second');
        vi.advanceTimersByTime(250);
        fixture.detectChanges();

        expect(display()).toBe('0:00');
        expect(expiredSpy).toHaveBeenCalledTimes(2);
    });

    it('should show an empty bar for a question without a limit', () => {
        fixture.componentRef.setInput('totalSeconds', 0);
        fixture.detectChanges();

        expect(barValue()).toBe('0');
    });

    it('should clear the interval on destroy', () => {
        const clearIntervalSpy = vi.spyOn(window, 'clearInterval');
        const intervalId = component['intervalId'];
        expect(intervalId).toBeDefined();

        fixture.destroy();

        expect(clearIntervalSpy).toHaveBeenCalledWith(intervalId);
        expect(component['intervalId']).toBeUndefined();

        nowSpy.mockClear();
        vi.advanceTimersByTime(1000);
        expect(nowSpy).not.toHaveBeenCalled();
    });
});
