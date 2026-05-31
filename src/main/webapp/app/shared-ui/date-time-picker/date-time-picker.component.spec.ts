import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import dayjs from 'dayjs/esm';
import { MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
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
            imports: [OwlNativeDateTimeModule, MockPipe(ArtemisTranslatePipe), MockModule(NgbTooltipModule), FormDateTimePickerComponent],
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

            expect(component.value()).toEqual(normalDateAsDateObject);
        });

        it('should write the correct date if date is date object', () => {
            component.writeValue(normalDateAsDateObject);

            expect(component.value()).toEqual(normalDateAsDateObject);
        });
    });

    it('should register callback function', () => {
        const onChangeSpy = vi.fn();
        component.registerOnChange(onChangeSpy);

        (component as any).onChange?.(normalDate);

        expect(onChangeSpy).toHaveBeenCalledOnce();
        expect(onChangeSpy).toHaveBeenCalledWith(normalDate);
    });

    it('should update field', () => {
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

    it('should clear the datepicker value', () => {
        const resetSpy = vi.spyOn(component.dateInput, 'reset').mockImplementation(() => undefined);
        const updateSignalsSpy = vi.spyOn(component, 'updateSignals').mockImplementation(() => undefined);

        component.clearDate();

        expect(resetSpy).toHaveBeenCalledWith(undefined);
        expect(updateSignalsSpy).toHaveBeenCalled();
    });
});
