import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CountdownComponent } from 'app/shared/countdown/countdown.component';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import dayjs from 'dayjs/esm';
import { EventEmitter } from '@angular/core';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockPipe } from 'ng-mocks';

describe('CountdownComponent', () => {
    let component: CountdownComponent;
    let fixture: ComponentFixture<CountdownComponent>;
    let mockNow: dayjs.Dayjs = dayjs();
    const advanceTimeBySeconds = (seconds: number) => {
        mockNow = mockNow.add(seconds, 'seconds');
    };
    let onFinishEmitSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CountdownComponent, MockPipe(ArtemisDatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, ArtemisServerDateService],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CountdownComponent);
                component = fixture.componentInstance;
                // initially, the countdown is for 10s
                component.targetDate = mockNow.add(10, 'seconds');
                jest.spyOn(TestBed.inject(ArtemisServerDateService), 'now').mockImplementation(() => mockNow);
                component.onFinish = new EventEmitter<void>();
                onFinishEmitSpy = jest.spyOn(component.onFinish, 'emit');
                jest.useFakeTimers();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        jest.useRealTimers();
    });

    it('should update displayed times and emit reachedZero event', async () => {
        fixture.detectChanges();
        expect(component.timeUntilTarget).toBe('10 s');

        advanceTimeBySeconds(5);
        component.updateDisplayedTimes();
        expect(component.timeUntilTarget).toBe('5 s');
        expect(onFinishEmitSpy).toHaveBeenCalledTimes(0);

        advanceTimeBySeconds(5);
        component.updateDisplayedTimes();
        expect(component.timeUntilTarget).toBe('artemisApp.showStatistic.now');
        expect(onFinishEmitSpy).toHaveBeenCalledOnce();
    });

    it('should clear interval when component is destroyed', () => {
        const clearIntervalSpy = jest.spyOn(window, 'clearInterval');
        component.interval = 123;
        component.ngOnDestroy();
        expect(clearIntervalSpy).toHaveBeenCalledWith(123);
    });

    it('should correctly calculate remaining time in seconds', () => {
        expect(component.remainingTimeSeconds()).toBe(10);
    });

    it('should correctly determine if countdown has reached zero', () => {
        expect(component.hasReachedZero()).toBeFalse();
        component.targetDate = mockNow.subtract(10, 'seconds');
        expect(component.hasReachedZero()).toBeTrue();
    });

    it('should fire event when targetDate changes', () => {
        advanceTimeBySeconds(10);
        component.updateDisplayedTimes();
        expect(component.timeUntilTarget).toBe('artemisApp.showStatistic.now');
        expect(onFinishEmitSpy).toHaveBeenCalledOnce();
    });

    it('should fire event only once when countdown continues to exist', () => {
        advanceTimeBySeconds(10);
        component.updateDisplayedTimes();
        expect(onFinishEmitSpy).toHaveBeenCalledOnce();

        advanceTimeBySeconds(15);
        component.updateDisplayedTimes();
        // still only once
        expect(onFinishEmitSpy).toHaveBeenCalledOnce();
    });

    it('should fire event multiple times when countdown is over and restarted', () => {
        advanceTimeBySeconds(10);
        component.updateDisplayedTimes();
        expect(component.timeUntilTarget).toBe('artemisApp.showStatistic.now');
        expect(onFinishEmitSpy).toHaveBeenCalledOnce();

        // Restart countdown
        component.targetDate = mockNow.add(5, 'seconds');
        component.updateDisplayedTimes();

        advanceTimeBySeconds(5);
        component.updateDisplayedTimes();
        expect(component.timeUntilTarget).toBe('artemisApp.showStatistic.now');
        expect(onFinishEmitSpy).toHaveBeenCalledTimes(2);
    });

    it('should show artemisApp.showStatistic.now when the countdown is over', () => {
        advanceTimeBySeconds(10);
        component.updateDisplayedTimes();
        expect(component.timeUntilTarget).toBe('artemisApp.showStatistic.now');

        advanceTimeBySeconds(10);
        component.updateDisplayedTimes();
        expect(component.timeUntilTarget).toBe('artemisApp.showStatistic.now');
    });
});
