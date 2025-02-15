import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { ResultProgressBarComponent } from '../../../../../main/webapp/app/exercises/shared/result/result-progress-bar/result-progress-bar.component';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ResultProgressBarComponent', () => {
    let component: ResultProgressBarComponent;
    let fixture: ComponentFixture<ResultProgressBarComponent>;
    let clearIntervalSpy: jest.SpyInstance;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ResultProgressBarComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ResultProgressBarComponent);
        component = fixture.componentInstance;

        clearIntervalSpy = jest.spyOn(window, 'clearInterval');

        fixture.componentRef.setInput('estimatedRemaining', 10);
        fixture.componentRef.setInput('estimatedDuration', 20);
        fixture.componentRef.setInput('isBuilding', false);
        fixture.componentRef.setInput('isQueued', true);

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllTimers();
        jest.useRealTimers();
    });

    it('should clear interval when not queued or building', fakeAsync(() => {
        fixture.componentRef.setInput('isBuilding', false);
        fixture.componentRef.setInput('isQueued', false);
        fixture.detectChanges();

        expect(clearIntervalSpy).toHaveBeenCalledWith(component.estimatedDurationInterval);
    }));

    it('should set queue progress bar', fakeAsync(() => {
        jest.useFakeTimers();
        fixture.componentRef.setInput('estimatedDuration', 25);
        fixture.detectChanges();

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
        fixture.componentRef.setInput('estimatedDuration', undefined);
        fixture.componentRef.setInput('estimatedRemaining', undefined);
        fixture.detectChanges();

        expect(component.isQueueProgressBarAnimated).toBeFalse();
        expect(component.queueProgressBarValue).toBe(100);

        jest.advanceTimersByTime(1500);

        expect(component.queueProgressBarOpacity).toBe(0);
    }));

    it('should alternate opacity when building', fakeAsync(() => {
        component.buildProgressBarOpacity = 1;

        jest.useFakeTimers();
        fixture.componentRef.setInput('isBuilding', true);
        fixture.componentRef.setInput('isQueued', false);
        fixture.componentRef.setInput('estimatedDuration', undefined);
        fixture.componentRef.setInput('estimatedRemaining', undefined);
        fixture.detectChanges();

        expect(component.isBuildProgressBarAnimated).toBeFalse();
        expect(component.buildProgressBarValue).toBe(100);

        jest.advanceTimersByTime(1500);

        expect(component.buildProgressBarOpacity).toBe(0);
    }));

    it('should clear interval on destroy', fakeAsync(() => {
        component.ngOnDestroy();
        expect(clearIntervalSpy).toHaveBeenCalledWith(component.estimatedDurationInterval);
    }));
});
