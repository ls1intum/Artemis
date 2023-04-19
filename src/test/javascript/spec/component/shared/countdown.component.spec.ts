import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CountdownComponent } from 'app/shared/countdown/countdown.component';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import dayjs from 'dayjs/esm';
import { EventEmitter } from '@angular/core';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockPipe } from 'ng-mocks';
import { MockArtemisServerDateService } from '../../helpers/mocks/service/mock-server-date.service';

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
            providers: [{ provide: ArtemisServerDateService, useClass: MockArtemisServerDateService }],
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

    it('should emit reachedZero event when countdown is over', async () => {
        component.update();
        expect(component.remainingTimeSeconds).toBe(10);

        advanceTimeBySeconds(5);
        component.update();
        expect(onFinishEmitSpy).toHaveBeenCalledTimes(0);

        advanceTimeBySeconds(5);
        component.update();
        expect(onFinishEmitSpy).toHaveBeenCalledOnce();
    });

    it('should clear interval when component is destroyed', () => {
        const clearIntervalSpy = jest.spyOn(window, 'clearInterval');
        component.interval = 123;
        component.ngOnDestroy();
        expect(clearIntervalSpy).toHaveBeenCalledWith(123);
    });

    it('should correctly calculate remaining time in seconds', () => {
        expect(component.calculateRemainingTimeSeconds()).toBe(10);
    });

    it('should correctly determine if countdown has reached zero', () => {
        expect(component.hasReachedZero()).toBeFalse();
        component.targetDate = mockNow.subtract(10, 'seconds');
        component.update();
        expect(component.hasReachedZero()).toBeTrue();
    });

    it('should fire event when targetDate changes', () => {
        component.targetDate = mockNow.subtract(5, 'seconds');
        component.update();
        expect(onFinishEmitSpy).toHaveBeenCalledOnce();
    });

    it('should fire event only once when countdown continues to exist', () => {
        advanceTimeBySeconds(10);
        component.update();
        expect(onFinishEmitSpy).toHaveBeenCalledOnce();

        advanceTimeBySeconds(15);
        component.update();
        // still only once
        expect(onFinishEmitSpy).toHaveBeenCalledOnce();
    });

    it('should fire event multiple times when countdown is over and restarted', () => {
        advanceTimeBySeconds(10);
        component.update();
        expect(onFinishEmitSpy).toHaveBeenCalledOnce();

        // Restart countdown
        component.targetDate = mockNow.add(5, 'seconds');
        component.update();

        advanceTimeBySeconds(5);
        component.update();
        expect(onFinishEmitSpy).toHaveBeenCalledTimes(2);
    });
});
