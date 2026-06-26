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

            // dayjs is normalised to a native Date because p-datepicker binds a native Date
            expect(component.value()).toEqual(normalDateAsDateObject);
        });

        it('should write the correct date if date is date object', () => {
            component.writeValue(normalDateAsDateObject);

            expect(component.value()).toEqual(normalDateAsDateObject);
        });

        it('should write the correct date if date is a UTC ISO string', () => {
            component.writeValue('2022-01-02T22:15:00.000Z');

            expect(component.value()).toEqual(normalDateAsDateObject);
        });

        it('should write undefined without conversion', () => {
            component.writeValue(undefined);

            expect(component.value()).toBeUndefined();
        });

        it('should write null without conversion', () => {
            component.writeValue(null);

            expect(component.value()).toBeNull();
        });

        it('should write null when the value is an invalid date', () => {
            component.writeValue('not-a-date');

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

    describe('UTC instant preservation across timezones', () => {
        /**
         * The picker binds a native local `Date` and edits in the browser-local timezone, while
         * Artemis persists UTC dayjs instants. A correct migration must NOT add or subtract the
         * timezone offset on either leg — the absolute instant the user picks must equal the
         * absolute instant persisted. These tests fail if the offset is double-applied (treating a
         * local Date as if it were UTC, or vice versa).
         */
        it('should bind a native Date that represents the same absolute instant as the UTC dayjs value (READ leg)', () => {
            component.writeValue(normalDate);

            const bound = component.value() as Date;
            expect(bound).toBeInstanceOf(Date);
            // Same absolute instant: epoch millis are identical regardless of the host timezone.
            expect(bound.getTime()).toBe(normalDate.valueOf());
            expect(bound.toISOString()).toBe(normalDate.toISOString());
        });

        it('should emit a dayjs at the exact instant the picker reports, with no offset drift (WRITE leg)', () => {
            const onChangeSpy = vi.fn();
            component.registerOnChange(onChangeSpy);
            vi.spyOn(component, 'valueChanged').mockImplementation(() => undefined);

            // Simulate p-datepicker emitting the local Date for the wall-clock the user selected.
            const pickedLocalDate = normalDateAsDateObject;
            component.updateField(pickedLocalDate);

            expect(onChangeSpy).toHaveBeenCalledOnce();
            const emitted = onChangeSpy.mock.calls[0][0] as dayjs.Dayjs;
            expect(dayjs.isDayjs(emitted)).toBe(true);
            // The persisted UTC instant equals exactly what the picker reported — no off-by-offset.
            expect(emitted.valueOf()).toBe(pickedLocalDate.getTime());
            expect(emitted.toISOString()).toBe(normalDate.toISOString());
        });

        it('should survive a full read -> pick -> read round-trip without drifting (idempotent)', () => {
            const onChangeSpy = vi.fn();
            component.registerOnChange(onChangeSpy);
            vi.spyOn(component, 'valueChanged').mockImplementation(() => undefined);

            // READ: server gives us a UTC dayjs.
            component.writeValue(normalDate);
            const boundDate = component.value() as Date;

            // PICK: the picker re-emits that very Date (user opened and confirmed without changing it).
            component.updateField(boundDate);

            const emitted = onChangeSpy.mock.calls[0][0] as dayjs.Dayjs;
            expect(emitted.toISOString()).toBe(normalDate.toISOString());

            // WRITE BACK: persisting and re-reading must again yield the same instant.
            component.writeValue(emitted);
            expect((component.value() as Date).getTime()).toBe(normalDate.valueOf());
        });

        it('should parse a UTC ISO string passed to updateField as the correct absolute instant', () => {
            const onChangeSpy = vi.fn();
            component.registerOnChange(onChangeSpy);
            vi.spyOn(component, 'valueChanged').mockImplementation(() => undefined);

            component.updateField('2022-01-02T22:15:00.000Z');

            const emitted = onChangeSpy.mock.calls[0][0] as dayjs.Dayjs;
            expect(emitted.toISOString()).toBe(normalDate.toISOString());
        });
    });

    describe('updateField', () => {
        it('should keep the bound value a native Date and emit the equivalent dayjs', () => {
            const onChangeSpy = vi.fn();
            component.registerOnChange(onChangeSpy);
            const valueChangedStub = vi.spyOn(component, 'valueChanged').mockImplementation(() => undefined);
            const newDate = normalDate.add(2, 'days');
            fixture.componentRef.setInput('value', normalDate);
            fixture.changeDetectorRef.detectChanges();

            component.updateField(newDate.toDate());

            expect(component.value()).toEqual(newDate.toDate());
            expect(onChangeSpy).toHaveBeenCalledOnce();
            const emitted = onChangeSpy.mock.calls[0][0] as dayjs.Dayjs;
            expect(emitted.toISOString()).toBe(newDate.toISOString());
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
            fixture.componentRef.setInput('error', true);
            expect(component.isValid()).toBe(false);
        });

        it('should be invalid when a required field has no value', () => {
            fixture.componentRef.setInput('requiredField', true);
            expect(component.isValid()).toBe(false);
        });

        it('should be invalid when the warning input is set', () => {
            fixture.componentRef.setInput('warning', true);
            expect(component.isValid()).toBe(false);
        });

        it('should be valid when a required field has a valid value and no error/warning is present', () => {
            fixture.componentRef.setInput('requiredField', true);
            fixture.componentRef.setInput('value', dayjs('2022-01-02'));
            expect(component.isValid()).toBe(true);
        });
    });

    it('should clear the datepicker value', () => {
        const resetSpy = vi.spyOn(component.dateInput, 'reset').mockImplementation(() => undefined);
        const onChangeSpy = vi.fn();
        component.registerOnChange(onChangeSpy);
        const valueChangeSpy = vi.spyOn(component.valueChange, 'emit');

        component.clearDate();

        expect(resetSpy).toHaveBeenCalledWith(undefined);
        expect(component.value()).toBeUndefined();
        expect(onChangeSpy).toHaveBeenCalledWith(undefined);
        // Clearing must notify consumers (audits / finished-builds filters) so they revalidate.
        expect(valueChangeSpy).toHaveBeenCalledOnce();
    });
});
