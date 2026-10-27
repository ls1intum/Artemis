import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiDatePickerComponent } from '@tumaet/ui-angular';
import dayjs from 'dayjs/esm';
import { ProgrammingExerciseBuildTimeWindowComponent } from 'app/programming/manage/update/update-components/build-time-window/programming-exercise-build-time-window.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { BuildTimeWindowError } from 'app/programming/shared/utils/build-time-window.utils';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ProgrammingExerciseBuildTimeWindowComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseBuildTimeWindowComponent>;
    let component: ProgrammingExerciseBuildTimeWindowComponent;
    let exercise: ProgrammingExercise;

    const start = dayjs('2026-10-01T10:00:00Z');
    const end = dayjs('2026-10-01T12:00:00Z');

    const pickers = () =>
        fixture.debugElement.queryAll(By.directive(TumAetUiDatePickerComponent)).map((debugElement) => debugElement.componentInstance as TumAetUiDatePickerComponent);
    const errorElement = () => fixture.nativeElement.querySelector('[data-testid="build-time-window-error"]') as HTMLElement | null;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ProgrammingExerciseBuildTimeWindowComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        exercise = new ProgrammingExercise(undefined, undefined);
        fixture = TestBed.createComponent(ProgrammingExerciseBuildTimeWindowComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('programmingExercise', exercise);
        fixture.detectChanges();
    });

    it('renders two labelled date pickers that are empty without a window', () => {
        const [startPicker, endPicker] = pickers();

        expect(pickers()).toHaveLength(2);
        expect(startPicker.value()).toBeUndefined();
        expect(endPicker.value()).toBeUndefined();
        expect(fixture.nativeElement.querySelector('#build-time-window-start')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('#build-time-window-end')).not.toBeNull();
        expect(errorElement()).toBeNull();
        expect(component.isInputValid()).toBe(true);
    });

    it('shows the window of the exercise', () => {
        // a different exercise object, as when the form replaces the exercise
        const other = new ProgrammingExercise(undefined, undefined);
        other.buildConfig!.advancedSettings = { buildTimeWindow: { start, end } };
        fixture.componentRef.setInput('programmingExercise', other);
        fixture.detectChanges();

        const [startPicker, endPicker] = pickers();
        expect(startPicker.value()?.isSame(start)).toBe(true);
        expect(endPicker.value()?.isSame(end)).toBe(true);
    });

    it('writes a changed start and end to the build config', () => {
        const [startPicker, endPicker] = pickers();

        startPicker.value.set(start);
        fixture.detectChanges();
        expect(exercise.buildConfig!.advancedSettings?.buildTimeWindow?.start?.isSame(start)).toBe(true);
        expect(exercise.buildConfig!.advancedSettings?.buildTimeWindow?.end).toBeUndefined();

        endPicker.value.set(end);
        fixture.detectChanges();
        expect(exercise.buildConfig!.advancedSettings?.buildTimeWindow?.end?.isSame(end)).toBe(true);
        expect(exercise.buildConfig!.advancedSettings?.buildTimeWindow?.start?.isSame(start)).toBe(true);
    });

    it('keeps the bound that another editor changed in the meantime when the other bound is edited', () => {
        const [startPicker] = pickers();
        const syncedEnd = dayjs('2026-10-01T18:00:00Z');
        // the metadata synchronization replaces the window on the same exercise object, which the pickers do not observe
        exercise.buildConfig!.advancedSettings = { buildTimeWindow: { start, end: syncedEnd } };

        startPicker.value.set(dayjs('2026-10-01T09:00:00Z'));
        fixture.detectChanges();

        expect(exercise.buildConfig!.advancedSettings?.buildTimeWindow?.end?.isSame(syncedEnd)).toBe(true);
    });

    it('removes the advanced settings when both bounds are cleared, which means no window', () => {
        const [startPicker, endPicker] = pickers();
        startPicker.value.set(start);
        endPicker.value.set(end);
        fixture.detectChanges();
        expect(exercise.buildConfig!.advancedSettings).toBeDefined();

        startPicker.value.set(undefined);
        fixture.detectChanges();
        expect(exercise.buildConfig!.advancedSettings?.buildTimeWindow?.end?.isSame(end)).toBe(true);

        endPicker.value.set(undefined);
        fixture.detectChanges();
        expect(exercise.buildConfig!.advancedSettings).toBeUndefined();
    });

    it('does nothing when the exercise has no build config', () => {
        exercise.buildConfig = undefined;

        expect(() => {
            pickers()[0].value.set(start);
            fixture.detectChanges();
        }).not.toThrow();
    });

    it.each([
        [BuildTimeWindowError.END_NOT_AFTER_START, 'artemisApp.programmingExercise.buildTimeWindow.endNotAfterStart'],
        [BuildTimeWindowError.OUTSIDE_EXERCISE_DATES, 'artemisApp.programmingExercise.buildTimeWindow.outsideExerciseDates'],
    ])('shows the problem %s next to the pickers', (error, translationKey) => {
        fixture.componentRef.setInput('error', error);
        fixture.detectChanges();

        expect(errorElement()).not.toBeNull();
        expect(errorElement()!.textContent).toContain(translationKey);

        fixture.componentRef.setInput('error', undefined);
        fixture.detectChanges();
        expect(errorElement()).toBeNull();
    });

    it('reports typed text that is not a date yet as invalid input', () => {
        const startField: HTMLInputElement = fixture.nativeElement.querySelector('#build-time-window-start');

        startField.value = '01.10.';
        startField.dispatchEvent(new Event('input'));
        startField.dispatchEvent(new Event('blur'));
        fixture.detectChanges();

        expect(component.isInputValid()).toBe(false);
    });
});
