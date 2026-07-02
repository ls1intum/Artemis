import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DateTimePickerType, FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { DatePicker } from 'primeng/datepicker';
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

        it('should clear a stale invalid state when an equal-empty value is written (programmatic reset)', () => {
            // empty field; user types unparseable text
            component.updateField('not-a-date');
            expect(component.dateInput.valid).toBe(false);

            // parent resets the control: writes undefined while value() is already empty (equal write)
            component.writeValue(undefined);

            expect(component.dateInput.valid).toBe(true);
        });
    });

    describe('dateInput.valid compatibility accessor', () => {
        it('should report an empty required field as invalid', () => {
            fixture.componentRef.setInput('requiredField', true);
            fixture.changeDetectorRef.detectChanges();

            expect(component.dateInput.valid).toBe(false);
        });

        it('should report a filled required field as valid', () => {
            fixture.componentRef.setInput('requiredField', true);
            component.updateField(normalDateAsDateObject);
            fixture.changeDetectorRef.detectChanges();

            expect(component.dateInput.valid).toBe(true);
        });
    });

    describe('invalid border rendering', () => {
        // The red border is driven by a class on the wrapper element (not only the inner p-datepicker
        // [invalid] input) so it stays in sync with the message under zoneless change detection (PR #13009).
        const wrapper = () => fixture.nativeElement.querySelector('[data-testid="date-picker-wrapper"]') as HTMLElement;
        const hasInvalidClass = () => wrapper().classList.contains('invalid-date-input');

        it('does not mark a valid value as invalid', () => {
            component.writeValue(normalDateAsDateObject);
            fixture.detectChanges();

            expect(hasInvalidClass()).toBe(false);
        });

        it('marks the wrapper invalid for unparseable typed text', () => {
            component.updateField('not-a-date');
            fixture.detectChanges();

            expect(hasInvalidClass()).toBe(true);
        });

        it('marks the wrapper invalid when the parent passes error()', () => {
            component.writeValue(normalDateAsDateObject);
            fixture.componentRef.setInput('error', true);
            fixture.detectChanges();

            expect(hasInvalidClass()).toBe(true);
        });

        it('marks an empty required field invalid', () => {
            fixture.componentRef.setInput('requiredField', true);
            fixture.detectChanges();

            expect(hasInvalidClass()).toBe(true);
        });

        it('does not show the red invalid border for a warning-only state (warning has its own styling)', () => {
            component.writeValue(normalDateAsDateObject);
            fixture.componentRef.setInput('warning', true);
            fixture.detectChanges();

            expect(hasInvalidClass()).toBe(false);
        });
    });

    describe('time picker confirm/close affordance', () => {
        const innerPicker = () => fixture.debugElement.query((de) => de.componentInstance instanceof DatePicker).componentInstance as DatePicker;

        // Open the overlay by toggling the panel directly. detectChanges(false) skips the dev-mode
        // "changed after checked" assertion, which the picker's overlay-open focus state churn would
        // otherwise trip in the test harness (not a production concern).
        async function openPanel(picker: DatePicker) {
            picker.overlayVisible = true;
            fixture.detectChanges(false);
            await fixture.whenStable();
            fixture.detectChanges(false);
        }

        it('commits the shown time and closes when the time-only confirm button is clicked (one-click apply)', async () => {
            fixture.componentRef.setInput('pickerType', DateTimePickerType.TIMER);
            fixture.detectChanges();
            const picker = innerPicker();
            await openPanel(picker);

            const button = document.body.querySelector('.p-datepicker-buttonbar button') as HTMLButtonElement | null;
            expect(button).not.toBeNull();
            expect(component.value()).toBeUndefined(); // nothing applied yet

            const hideSpy = vi.spyOn(picker, 'hideOverlay');
            button!.click();

            expect(hideSpy).toHaveBeenCalledOnce();
            expect(picker.overlayVisible).toBe(false);
            // The displayed (default / current) time is committed in a single click, instead of requiring
            // the user to first nudge a spinner field.
            expect(component.value()).toBeDefined();
        });

        it('does not overwrite an already-selected time when the confirm button is clicked', async () => {
            fixture.componentRef.setInput('pickerType', DateTimePickerType.TIMER);
            const chosen = new Date('2022-01-02T22:15:00');
            component.writeValue(chosen);
            fixture.detectChanges();
            const picker = innerPicker();
            await openPanel(picker);

            (document.body.querySelector('.p-datepicker-buttonbar button') as HTMLButtonElement).click();

            expect(dayjs(component.value() as Date).isSame(chosen)).toBe(true);
        });

        it('does not render a button bar for the date-only (CALENDAR) picker', async () => {
            fixture.componentRef.setInput('pickerType', DateTimePickerType.CALENDAR);
            fixture.detectChanges();
            const picker = innerPicker();
            await openPanel(picker);

            expect(picker.showButtonBar).toBe(false);
            expect(document.body.querySelector('.p-datepicker-buttonbar')).toBeNull();
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
            const onChangeSpy = vi.fn();
            component.registerOnChange(onChangeSpy);
            component.updateField(normalDateAsDateObject);
            component.updateField('not-a-date');
            expect(component.dateInput.valid).toBe(false);
            onChangeSpy.mockClear();

            // re-typing the same (still-current) date must clear the invalid state AND re-emit
            // onChange so the parent form model (currently undefined after the invalid entry)
            // is re-synced — even though this.value() still holds the previous date.
            component.updateField(normalDateAsDateObject);

            expect(component.dateInput.valid).toBe(true);
            expect(component.value()).toEqual(normalDateAsDateObject);
            expect(onChangeSpy).toHaveBeenCalledOnce();
            expect(onChangeSpy).toHaveBeenCalledWith(dayjs(normalDateAsDateObject));
        });

        it('should reject a typed date before [min] and not propagate it to the parent', () => {
            const minDate = normalDate.subtract(0, 'day'); // min = normalDate itself
            fixture.componentRef.setInput('min', minDate);
            fixture.detectChanges();

            const onChangeSpy = vi.fn();
            component.registerOnChange(onChangeSpy);
            const beforeMin = new Date(normalDateAsDateObject.getTime() - 24 * 60 * 60 * 1000); // 1 day before

            component.updateField(beforeMin);

            expect(component.dateInput.valid).toBe(false);
            expect(onChangeSpy).toHaveBeenCalledWith(undefined);
            expect(onChangeSpy).not.toHaveBeenCalledWith(expect.objectContaining({ $d: expect.any(Date) }));
        });

        it('should reject a typed date after [max] and not propagate it to the parent', () => {
            const maxDate = normalDate.add(0, 'day'); // max = normalDate itself
            fixture.componentRef.setInput('max', maxDate);
            fixture.detectChanges();

            const onChangeSpy = vi.fn();
            component.registerOnChange(onChangeSpy);
            const afterMax = new Date(normalDateAsDateObject.getTime() + 24 * 60 * 60 * 1000); // 1 day after

            component.updateField(afterMax);

            expect(component.dateInput.valid).toBe(false);
            expect(onChangeSpy).toHaveBeenCalledWith(undefined);
        });

        it('should accept a typed date equal to [min]', () => {
            fixture.componentRef.setInput('min', normalDate);
            fixture.detectChanges();

            const onChangeSpy = vi.fn();
            component.registerOnChange(onChangeSpy);

            component.updateField(normalDateAsDateObject);

            expect(component.dateInput.valid).toBe(true);
            expect(onChangeSpy).toHaveBeenCalledWith(dayjs(normalDateAsDateObject));
        });

        it('should recover and re-emit after a prior out-of-range rejection', () => {
            const minDate = normalDate;
            fixture.componentRef.setInput('min', minDate);
            fixture.detectChanges();

            const onChangeSpy = vi.fn();
            component.registerOnChange(onChangeSpy);

            const beforeMin = new Date(normalDateAsDateObject.getTime() - 24 * 60 * 60 * 1000);
            const validDate = new Date(normalDateAsDateObject.getTime() + 24 * 60 * 60 * 1000);

            // Reject out-of-range → then enter a valid in-range date → must propagate
            component.updateField(beforeMin);
            expect(onChangeSpy).toHaveBeenLastCalledWith(undefined);
            onChangeSpy.mockClear();

            component.updateField(validDate);

            expect(component.dateInput.valid).toBe(true);
            expect(onChangeSpy).toHaveBeenCalledWith(dayjs(validDate));
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
