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
                jest.spyOn(TestBed.inject(ArtemisServerDateService), 'now').mockImplementation(() => mockNow);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should update displayed times and emit reachedZero event', async () => {
        component.targetDate = mockNow.add(10, 'seconds');
        component.onFinish = new EventEmitter<void>();

        const emitSpy = jest.spyOn(component.onFinish, 'emit');

        fixture.detectChanges();
        jest.useFakeTimers();

        expect(component.timeUntilTarget).toBe('10 s');

        advanceTimeBySeconds(5);
        component.updateDisplayedTimes();
        expect(component.timeUntilTarget).toBe('5 s');
        expect(emitSpy).toHaveBeenCalledTimes(0);

        advanceTimeBySeconds(5);
        component.updateDisplayedTimes();
        expect(component.timeUntilTarget).toBe('artemisApp.showStatistic.now');
        expect(emitSpy).toHaveBeenCalledOnce();

        jest.useRealTimers();
    });

    it('should clear interval when component is destroyed', () => {
        const clearIntervalSpy = jest.spyOn(window, 'clearInterval');
        component.interval = 123;
        component.ngOnDestroy();
        expect(clearIntervalSpy).toHaveBeenCalledWith(123);
    });

    it('should correctly calculate remaining time in seconds', () => {
        component.targetDate = dayjs(mockNow).add(10, 'seconds');
        expect(component.remainingTimeSeconds()).toBe(10);
    });

    it('should correctly determine if countdown has reached zero', () => {
        component.targetDate = dayjs(mockNow).add(10, 'seconds');
        expect(component.hasReachedZero()).toBeFalse();

        component.targetDate = dayjs(mockNow).subtract(10, 'seconds');
        expect(component.hasReachedZero()).toBeTrue();
    });

    it('should fire event when targetDate changes', () => {
        component.targetDate = mockNow.add(10, 'seconds');
        component.onFinish = new EventEmitter<void>();
        const emitSpy = jest.spyOn(component.onFinish, 'emit');

        fixture.detectChanges();
        jest.useFakeTimers();

        component.targetDate = mockNow.add(15, 'seconds');
        fixture.detectChanges();

        advanceTimeBySeconds(15);
        component.updateDisplayedTimes();
        expect(component.timeUntilTarget).toBe('artemisApp.showStatistic.now');
        expect(emitSpy).toHaveBeenCalledOnce();

        jest.useRealTimers();
    });

    it('should fire event only once when countdown continues to exist', () => {
        component.targetDate = mockNow.add(10, 'seconds');
        component.onFinish = new EventEmitter<void>();
        const emitSpy = jest.spyOn(component.onFinish, 'emit');

        fixture.detectChanges();
        jest.useFakeTimers();

        component.targetDate = mockNow.add(15, 'seconds');
        advanceTimeBySeconds(15);
        component.updateDisplayedTimes();
        expect(emitSpy).toHaveBeenCalledOnce();

        advanceTimeBySeconds(15);
        component.updateDisplayedTimes();
        // still only once
        expect(emitSpy).toHaveBeenCalledOnce();

        jest.useRealTimers();
    });

    it('should fire event multiple times when countdown is over and restarted', () => {
        component.targetDate = mockNow.add(10, 'seconds');
        component.onFinish = new EventEmitter<void>();
        const emitSpy = jest.spyOn(component.onFinish, 'emit');

        fixture.detectChanges();
        jest.useFakeTimers();

        advanceTimeBySeconds(10);
        component.updateDisplayedTimes();
        expect(component.timeUntilTarget).toBe('artemisApp.showStatistic.now');
        expect(emitSpy).toHaveBeenCalledOnce();

        // Restart countdown
        component.targetDate = mockNow.add(5, 'seconds');
        component.updateDisplayedTimes();

        advanceTimeBySeconds(5);
        component.updateDisplayedTimes();
        expect(component.timeUntilTarget).toBe('artemisApp.showStatistic.now');
        expect(emitSpy).toHaveBeenCalledTimes(2);

        jest.useRealTimers();
    });

    it('should show artemisApp.showStatistic.now when the countdown is over', () => {
        component.targetDate = dayjs(mockNow).add(10, 'seconds');
        jest.useFakeTimers();

        advanceTimeBySeconds(10);
        component.updateDisplayedTimes();
        expect(component.timeUntilTarget).toBe('artemisApp.showStatistic.now');

        advanceTimeBySeconds(10);
        component.updateDisplayedTimes();
        expect(component.timeUntilTarget).toBe('artemisApp.showStatistic.now');

        jest.useRealTimers();
    });
});
