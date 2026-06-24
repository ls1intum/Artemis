import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
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

            expect(component.value()).toEqual(normalDateAsDateObject);
        });

        it('should write the correct date if date is date object', () => {
            component.writeValue(normalDateAsDateObject);

            expect(component.value()).toEqual(normalDateAsDateObject);
        });

        it('should clear the value to null when undefined is written after a date was set', () => {
            component.writeValue(normalDateAsDateObject);
            expect(component.value()).toEqual(normalDateAsDateObject);

            component.writeValue(undefined);

            expect(component.value()).toBeNull();
            expect(component.dateInput.valid).toBe(true);
        });
    });

    it('should register callback function', () => {
        const onChangeSpy = vi.fn();
        component.registerOnChange(onChangeSpy);

        (component as any).onChange?.(normalDate);

        expect(onChangeSpy).toHaveBeenCalledOnce();
        expect(onChangeSpy).toHaveBeenCalledWith(normalDate);
    });

    describe('test updateField', () => {
        it('should accept a valid Date and emit the equivalent dayjs', () => {
            const onChangeSpy = vi.fn();
            component.registerOnChange(onChangeSpy);
            const valueChangedStub = vi.spyOn(component, 'valueChanged').mockImplementation(() => undefined);

            component.updateField(normalDateAsDateObject);

            expect(component.value()).toEqual(normalDateAsDateObject);
            expect(component.dateInput.valid).toBe(true);
            expect(onChangeSpy).toHaveBeenCalledOnce();
            expect(onChangeSpy).toHaveBeenCalledWith(dayjs(normalDateAsDateObject));
            expect(valueChangedStub).toHaveBeenCalledOnce();
        });

        it('should flag the field invalid for unparseable text and not emit a date', () => {
            const onChangeSpy = vi.fn();
            component.registerOnChange(onChangeSpy);

            component.updateField('not-a-date');

            expect(component.dateInput.valid).toBe(false);
            expect(onChangeSpy).toHaveBeenCalledWith(undefined);
        });

        it('should clear the value when null is emitted', () => {
            const onChangeSpy = vi.fn();
            component.registerOnChange(onChangeSpy);
            // seed a value first
            component.updateField(normalDateAsDateObject);
            onChangeSpy.mockClear();

            component.updateField(null);

            expect(component.value()).toBeNull();
            expect(component.dateInput.valid).toBe(true);
            expect(onChangeSpy).toHaveBeenCalledWith(undefined);
        });

        it('should recover validity when the same date is re-entered after unparseable text', () => {
            component.updateField(normalDateAsDateObject);
            component.updateField('not-a-date');
            expect(component.dateInput.valid).toBe(false);

            // re-typing the same (still-current) date must clear the invalid state, not stay stuck
            component.updateField(normalDateAsDateObject);

            expect(component.dateInput.valid).toBe(true);
            expect(component.value()).toEqual(normalDateAsDateObject);
        });

        it('should recover validity when an empty field with unparseable text is cleared', () => {
            component.updateField('not-a-date');
            expect(component.dateInput.valid).toBe(false);

            component.updateField('');

            expect(component.dateInput.valid).toBe(true);
            expect(component.value()).toBeUndefined();
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
});
