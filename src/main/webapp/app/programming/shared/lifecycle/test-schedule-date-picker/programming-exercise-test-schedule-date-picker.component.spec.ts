import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import dayjs from 'dayjs/esm';
import { ProgrammingExerciseTestScheduleDatePickerComponent } from 'app/programming/shared/lifecycle/test-schedule-date-picker/programming-exercise-test-schedule-date-picker.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ProgrammingExerciseTestScheduleDatePickerComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: ProgrammingExerciseTestScheduleDatePickerComponent;
    let fixture: ComponentFixture<ProgrammingExerciseTestScheduleDatePickerComponent>;

    const selectedDate = dayjs().add(5, 'days').toDate();
    const startAt = dayjs().add(6, 'days');
    const min = dayjs();
    const max = dayjs().add(7, 'days');
    const label = '';
    const tooltipText = '';
    const readOnly = false;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseTestScheduleDatePickerComponent);
        comp = fixture.componentInstance;

        comp.selectedDate = selectedDate;
        fixture.componentRef.setInput('startAt', startAt);
        fixture.componentRef.setInput('min', min);
        fixture.componentRef.setInput('max', max);
        fixture.componentRef.setInput('label', label);
        fixture.componentRef.setInput('tooltipText', tooltipText);
        fixture.componentRef.setInput('readOnly', readOnly);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should not change date when value is reference-equal to selected date', () => {
        const spy = vi.spyOn(comp, '_onChange');
        comp.writeValue(selectedDate);

        expect(spy).not.toHaveBeenCalled();
    });

    it('should update date with dayjs object and invoke change', () => {
        const updatedDayJsTime = dayjs(4, 'days');
        const updatedDateNumber = updatedDayJsTime.toDate().getDate();
        const spy = vi.spyOn(comp, '_onChange');
        comp.writeValue(updatedDayJsTime);

        expect(comp.selectedDate?.getDate()).toBe(updatedDateNumber);
        expect(spy).toHaveBeenCalledOnce();
    });

    it('should update date with date object and invoke change', () => {
        const updatedDayJsTime = dayjs(4, 'days');
        const updatedDateNumber = updatedDayJsTime.toDate().getDate();
        const spy = vi.spyOn(comp, '_onChange');
        comp.writeValue(updatedDayJsTime);

        expect(comp.selectedDate?.getDate()).toBe(updatedDateNumber);
        expect(spy).toHaveBeenCalledOnce();
    });

    it('should reset date and emit reset event', () => {
        const spy = vi.spyOn(comp.onDateReset, 'emit');
        comp.resetDate();

        expect(comp.selectedDate).toBeUndefined();
        expect(spy).toHaveBeenCalledOnce();
    });

    it('should call all functions', () => {
        const someFunction = vi.fn();
        comp.registerOnChange(someFunction);
        expect(comp._onChange).toBe(someFunction);
    });
});
