import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { ResultProgressBarComponent } from '../../../../../main/webapp/app/exercises/shared/result/result-progress-bar/result-progress-bar.component';
import { ArtemisTestModule } from '../../test.module';
import dayjs from 'dayjs/esm';

describe('ResultProgressBarComponent', () => {
    let component: ResultProgressBarComponent;
    let fixture: ComponentFixture<ResultProgressBarComponent>;
    let clearIntervalSpy: jest.SpyInstance;
    const now = dayjs();

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ResultProgressBarComponent, ArtemisTestModule],
        }).compileComponents();

        fixture = TestBed.createComponent(ResultProgressBarComponent);
        component = fixture.componentInstance;

        clearIntervalSpy = jest.spyOn(window, 'clearInterval');

        fixture.componentRef.setInput('estimatedCompletionDate', now.add(30, 'seconds'));
        fixture.componentRef.setInput('buildStartDate', now);
        fixture.componentRef.setInput('isBuilding', false);
        fixture.componentRef.setInput('isQueued', true);

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllTimers();
        jest.useRealTimers();
    });

    it('should clear interval when not queued or building', fakeAsync(() => {
        expect(component.estimatedDurationInterval).toBeDefined();

        fixture.componentRef.setInput('isBuilding', false);
        fixture.componentRef.setInput('isQueued', false);
        fixture.detectChanges();

        expect(clearIntervalSpy).toHaveBeenCalledWith(component.estimatedDurationInterval);
    }));

    it('should set queue progress bar', fakeAsync(() => {
        jest.useFakeTimers();
        fixture.componentRef.setInput('estimatedCompletionDate', now.add(40, 'seconds'));
        fixture.detectChanges();
        expect(component.estimatedDurationInterval).toBeDefined();

        expect(component.isBuildProgressBarAnimated).toBeTrue();
        expect(component.buildProgressBarOpacity).toBe(1);
        expect(component.buildProgressBarValue).toBe(0);

        jest.advanceTimersByTime(1500);

        expect(component.queueProgressBarValue).toBeGreaterThan(0);
        expect(component.queueProgressBarValue).toBeLessThan(100);
    }));

    it('should set build progress bar', fakeAsync(() => {
        jest.useFakeTimers();
        fixture.componentRef.setInput('isBuilding', true);
        fixture.componentRef.setInput('isQueued', false);
        fixture.detectChanges();
        expect(component.estimatedDurationInterval).toBeDefined();

        expect(component.isQueueProgressBarAnimated).toBeTrue();
        expect(component.queueProgressBarOpacity).toBe(1);
        expect(component.queueProgressBarValue).toBe(100);

        jest.advanceTimersByTime(1500);

        expect(component.buildProgressBarValue).toBeGreaterThan(0);
        expect(component.buildProgressBarValue).toBeLessThan(100);
    }));

    it('should alternate opacity when queued', fakeAsync(() => {
        component.queueProgressBarOpacity = 1;

        jest.useFakeTimers();
        fixture.componentRef.setInput('isBuilding', false);
        fixture.componentRef.setInput('isQueued', true);
        fixture.componentRef.setInput('estimatedCompletionDate', undefined);
        fixture.componentRef.setInput('buildStartDate', undefined);
        fixture.detectChanges();

        expect(component.isQueueProgressBarAnimated).toBeFalse();
        expect(component.queueProgressBarValue).toBe(100);
        expect(component.estimatedRemaining).toBe(0);

        jest.advanceTimersByTime(1500);

        expect(component.queueProgressBarOpacity).toBe(0);
    }));

    it('should alternate opacity when building', fakeAsync(() => {
        component.buildProgressBarOpacity = 1;

        jest.useFakeTimers();
        fixture.componentRef.setInput('isBuilding', true);
        fixture.componentRef.setInput('isQueued', false);
        fixture.componentRef.setInput('estimatedCompletionDate', undefined);
        fixture.componentRef.setInput('buildStartDate', undefined);
        fixture.detectChanges();

        expect(component.isBuildProgressBarAnimated).toBeFalse();
        expect(component.buildProgressBarValue).toBe(100);
        expect(component.estimatedRemaining).toBe(0);

        jest.advanceTimersByTime(1500);

        expect(component.buildProgressBarOpacity).toBe(0);
    }));

    it('should clear interval on destroy', fakeAsync(() => {
        expect(component.estimatedDurationInterval).toBeDefined();
        component.ngOnDestroy();
        expect(clearIntervalSpy).toHaveBeenCalledWith(component.estimatedDurationInterval);
    }));
});
