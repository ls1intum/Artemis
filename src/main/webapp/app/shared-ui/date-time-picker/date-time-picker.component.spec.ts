import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DateTimePickerType, FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import dayjs from 'dayjs/esm';
import { vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('FormDateTimePickerComponent', () => {
    setupTestBed({ zoneless: true });
    let component: FormDateTimePickerComponent;
    let fixture: ComponentFixture<FormDateTimePickerComponent>;

    const normalDate = dayjs('2022-01-02T22:15+00:00');
    const normalDateAsDateObject = new Date('2022-01-02T22:15+00:00');

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FormDateTimePickerComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(FormDateTimePickerComponent);
        component = fixture.componentInstance;
        component.dateInput = { reset: vi.fn() } as any;
    });

    it('should emit if a value is changed', () => {
        const emitStub = vi.spyOn(component.valueChange, 'emit').mockImplementation(() => undefined);

        component.valueChanged();

        expect(emitStub).toHaveBeenCalledOnce();
    });

    describe('test date conversion', () => {
        let convertedDate: Date | null;
        it('should convert the dayjs if it is not undefined', () => {
            convertedDate = component.convertToDate(normalDate);

            expect(convertedDate).toEqual(normalDateAsDateObject);
        });

        it('should return null if dayjs is undefined', () => {
            convertedDate = component.convertToDate();

            expect(convertedDate).toBeNull();
        });

        it('should return null if dayjs is invalid', () => {
            const unconvertedDate = dayjs('2022-31-02T00:00+00:00');

            expect(unconvertedDate.isValid()).toBe(false);

            convertedDate = component.convertToDate(unconvertedDate);

            expect(convertedDate).toBeNull();
        });
    });

    describe('test date writing', () => {
        it('should write the correct date if date is dayjs object', () => {
            component.writeValue(normalDate);

            // dayjs is converted to a Date because the underlying p-datepicker only works with Date objects
            expect(component.value()).toEqual(normalDateAsDateObject);
        });

        it('should write the correct date if date is date object', () => {
            component.writeValue(normalDateAsDateObject);

            expect(component.value()).toEqual(normalDateAsDateObject);
        });

        it('should write null without conversion', () => {
            component.writeValue(null);

            expect(component.value()).toBeNull();
        });
    });

    it('should register callback function', () => {
        const onChangeSpy = vi.fn();
        component.registerOnChange(onChangeSpy);

        (component as any).onChange?.(normalDate);

        expect(onChangeSpy).toHaveBeenCalledOnce();
        expect(onChangeSpy).toHaveBeenCalledWith(normalDate);
    });

    describe('updateField (p-datepicker emits a JS Date)', () => {
        it('should convert the Date emitted by the picker back to a UTC-correct dayjs', () => {
            const onChangeSpy = vi.fn();
            component.registerOnChange(onChangeSpy);
            const valueChangedStub = vi.spyOn(component, 'valueChanged').mockImplementation(() => undefined);

            component.updateField(normalDateAsDateObject);

            // value keeps the raw widget value (a Date)
            expect(component.value()).toEqual(normalDateAsDateObject);
            // the registered onChange receives a dayjs equivalent to the picked date (no offset drift)
            expect(onChangeSpy).toHaveBeenCalledOnce();
            const emitted = onChangeSpy.mock.calls[0][0] as dayjs.Dayjs;
            expect(dayjs.isDayjs(emitted)).toBe(true);
            expect(emitted.toISOString()).toEqual(normalDate.toISOString());
            expect(valueChangedStub).toHaveBeenCalledOnce();
        });

        it('should still propagate the value when given a dayjs instance', () => {
            const onChangeSpy = vi.fn();
            component.registerOnChange(onChangeSpy);
            const valueChangedStub = vi.spyOn(component, 'valueChanged').mockImplementation(() => undefined);
            const newDate = normalDate.add(2, 'days');
            fixture.componentRef.setInput('value', normalDate);
            fixture.changeDetectorRef.detectChanges();

            component.updateField(newDate);

            expect(component.value()).toEqual(newDate);
            expect(onChangeSpy).toHaveBeenCalledOnce();
            expect(onChangeSpy).toHaveBeenCalledWith(newDate);
            expect(valueChangedStub).toHaveBeenCalledOnce();
        });

        it('should emit undefined when the picker is cleared (null/undefined value)', () => {
            const onChangeSpy = vi.fn();
            component.registerOnChange(onChangeSpy);
            vi.spyOn(component, 'valueChanged').mockImplementation(() => undefined);

            component.updateField(null);

            expect(component.value()).toBeNull();
            expect(onChangeSpy).toHaveBeenCalledWith(undefined);
        });
    });

    it('should have working getters', () => {
        const expectedMinDate = normalDate.subtract(2, 'day');
        const expectedMaxDate = normalDate.add(2, 'day');
        const expectedStartDate = normalDate.add(1, 'day');

        fixture.componentRef.setInput('min', expectedMinDate);
        fixture.componentRef.setInput('max', expectedMaxDate);
        fixture.componentRef.setInput('startAt', expectedStartDate);
        const timeZone = component.currentTimeZone;

        expect(timeZone).toBeDefined();
        expect(dayjs(component.minDate())).toEqual(expectedMinDate);
        expect(dayjs(component.maxDate())).toEqual(expectedMaxDate);
        expect(dayjs(component.startDate())).toEqual(expectedStartDate);
    });

    describe('pickerType -> p-datepicker time flags', () => {
        it('should show date + time by default', () => {
            expect((component as any).showTime()).toBe(true);
            expect((component as any).timeOnly()).toBe(false);
        });

        it('should show date only for CALENDAR', () => {
            fixture.componentRef.setInput('pickerType', DateTimePickerType.CALENDAR);
            expect((component as any).showTime()).toBe(false);
            expect((component as any).timeOnly()).toBe(false);
        });

        it('should show time only for TIMER', () => {
            fixture.componentRef.setInput('pickerType', DateTimePickerType.TIMER);
            expect((component as any).showTime()).toBe(true);
            expect((component as any).timeOnly()).toBe(true);
        });
    });

    describe('validation', () => {
        it('should be invalid when the error input is set', () => {
            (component as any).isInputValid.set(true);
            fixture.componentRef.setInput('error', true);
            expect(component.isValid()).toBe(false);
        });

        it('should be invalid when a required field has no value', () => {
            (component as any).isInputValid.set(true);
            (component as any).dateInputValue.set('');
            fixture.componentRef.setInput('requiredField', true);
            expect(component.isValid()).toBe(false);
        });

        it('should be invalid when the warning input is set', () => {
            (component as any).isInputValid.set(true);
            fixture.componentRef.setInput('warning', true);
            expect(component.isValid()).toBe(false);
        });

        it('should be valid when input is valid and no error/warning/required violation is present', () => {
            (component as any).isInputValid.set(true);
            (component as any).dateInputValue.set('2022-01-02');
            expect(component.isValid()).toBe(true);
        });
    });

    it('should clear the datepicker value', () => {
        const resetSpy = vi.spyOn(component.dateInput, 'reset').mockImplementation(() => undefined);
        const onChangeSpy = vi.fn();
        component.registerOnChange(onChangeSpy);
        const updateSignalsSpy = vi.spyOn(component, 'updateSignals').mockImplementation(() => undefined);

        component.clearDate();

        expect(resetSpy).toHaveBeenCalledWith(undefined);
        expect(component.value()).toBeUndefined();
        expect(onChangeSpy).toHaveBeenCalledWith(undefined);
        expect(updateSignalsSpy).toHaveBeenCalled();
    });
});
