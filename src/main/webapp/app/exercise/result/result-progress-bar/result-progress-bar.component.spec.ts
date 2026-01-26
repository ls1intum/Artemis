import { expect, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ResultProgressBarComponent } from 'app/exercise/result/result-progress-bar/result-progress-bar.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ResultProgressBarComponent', () => {
    setupTestBed({ zoneless: true });
    let component: ResultProgressBarComponent;
    let fixture: ComponentFixture<ResultProgressBarComponent>;
    let clearIntervalSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ResultProgressBarComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ResultProgressBarComponent);
        component = fixture.componentInstance;

        clearIntervalSpy = vi.spyOn(window, 'clearInterval');

        fixture.componentRef.setInput('estimatedRemaining', 10);
        fixture.componentRef.setInput('estimatedDuration', 20);
        fixture.componentRef.setInput('isBuilding', false);
        fixture.componentRef.setInput('isQueued', true);

        fixture.detectChanges();
    });

    afterEach(() => {
        vi.clearAllTimers();
        vi.useRealTimers();
    });

    it('should clear interval when not queued or building', () => {
        fixture.componentRef.setInput('isBuilding', false);
        fixture.componentRef.setInput('isQueued', false);
        fixture.detectChanges();

        expect(clearIntervalSpy).toHaveBeenCalledWith(component.estimatedDurationInterval);
    });

    it('should set queue progress bar', () => {
        vi.useFakeTimers();
        fixture.componentRef.setInput('estimatedDuration', 25);
        fixture.detectChanges();

        expect(component.isBuildProgressBarAnimated).toBe(true);
        expect(component.buildProgressBarOpacity).toBe(1);
        expect(component.buildProgressBarValue).toBe(0);

        vi.advanceTimersByTime(1500);

        expect(component.queueProgressBarValue).toBeGreaterThan(0);
        expect(component.queueProgressBarValue).toBeLessThan(100);
    });

    it('should set build progress bar', () => {
        vi.useFakeTimers();
        fixture.componentRef.setInput('isBuilding', true);
        fixture.componentRef.setInput('isQueued', false);
        fixture.detectChanges();

        expect(component.isQueueProgressBarAnimated).toBe(true);
        expect(component.queueProgressBarOpacity).toBe(1);
        expect(component.queueProgressBarValue).toBe(100);

        vi.advanceTimersByTime(1500);

        expect(component.buildProgressBarValue).toBeGreaterThan(0);
        expect(component.buildProgressBarValue).toBeLessThan(100);
    });

    it('should alternate opacity when queued', () => {
        component.queueProgressBarOpacity = 1;

        vi.useFakeTimers();
        fixture.componentRef.setInput('isBuilding', false);
        fixture.componentRef.setInput('isQueued', true);
        fixture.componentRef.setInput('estimatedDuration', undefined);
        fixture.componentRef.setInput('estimatedRemaining', undefined);
        fixture.detectChanges();

        expect(component.isQueueProgressBarAnimated).toBe(false);
        expect(component.queueProgressBarValue).toBe(100);

        vi.advanceTimersByTime(1500);

        expect(component.queueProgressBarOpacity).toBe(0);
    });

    it('should alternate opacity when building', () => {
        component.buildProgressBarOpacity = 1;

        vi.useFakeTimers();
        fixture.componentRef.setInput('isBuilding', true);
        fixture.componentRef.setInput('isQueued', false);
        fixture.componentRef.setInput('estimatedDuration', undefined);
        fixture.componentRef.setInput('estimatedRemaining', undefined);
        fixture.detectChanges();

        expect(component.isBuildProgressBarAnimated).toBe(false);
        expect(component.buildProgressBarValue).toBe(100);

        vi.advanceTimersByTime(1500);

        expect(component.buildProgressBarOpacity).toBe(0);
    });

    it('should clear interval on destroy', () => {
        component.ngOnDestroy();
        expect(clearIntervalSpy).toHaveBeenCalledWith(component.estimatedDurationInterval);
    });
});
