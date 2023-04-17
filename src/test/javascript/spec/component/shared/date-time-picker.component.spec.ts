import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgModel } from '@angular/forms';
import { OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import dayjs from 'dayjs/esm';
import { MockDirective, MockModule } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';

describe('FormDateTimePickerComponent', () => {
    let component: FormDateTimePickerComponent;
    let fixture: ComponentFixture<FormDateTimePickerComponent>;

    const normalDate = dayjs('2022-01-02T22:15+00:00');
    const normalDateAsDateObject = new Date('2022-01-02T22:15+00:00');

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(OwlDateTimeModule)],
            declarations: [FormDateTimePickerComponent, MockDirective(NgModel)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FormDateTimePickerComponent);
                component = fixture.componentInstance;
            });
    });

    it('should emit if a value is changed', () => {
        const emitStub = jest.spyOn(component.valueChange, 'emit').mockImplementation();
        component.isInvalidDate = true;

        component.valueChanged();

        expect(emitStub).toHaveBeenCalledOnceWith(true);
    });

    describe('test date conversion', () => {
        let convertedDate: Date | null;
        it('should convert the dayjs if it is not undefined', () => {
            convertedDate = component.convert(normalDate);

            expect(convertedDate).toEqual(normalDateAsDateObject);
        });

        it('should return null if dayjs is undefined', () => {
            convertedDate = component.convert();

            expect(convertedDate).toBeNull();
        });

        it('should return null if dayjs is invalid', () => {
            const unconvertedDate = dayjs('2022-31-02T00:00+00:00');

            convertedDate = component.convert(unconvertedDate);

            expect(convertedDate).toBeNull();
        });
    });

    describe('test date writing', () => {
        it('should write the correct date if date is dayjs object', () => {
            component.writeValue(normalDate);

            expect(component.value).toEqual(normalDateAsDateObject);
        });

        it('should write the correct date if date is date object', () => {
            component.writeValue(normalDateAsDateObject);

            expect(component.value).toEqual(normalDateAsDateObject);
        });
    });

    it('should register callback function', () => {
        const testCallBackFunction = (date: dayjs.Dayjs) => 'I am a test callbackFunction: ' + date.toDate();

        component.registerOnChange(testCallBackFunction);

        expect(component._onChange(normalDate)).toBe(testCallBackFunction(normalDate));
    });

    describe('test update field', () => {
        it('should update field with valid date', () => {
            const valueChangedStub = jest.spyOn(component, 'valueChanged').mockImplementation();
            const onChangeSpy = jest.spyOn(component, '_onChange');
            component.value = normalDate.subtract(2, 'days');
            component.isInvalidDate = true;

            component.updateField(normalDateAsDateObject);

            expect(component.value).toEqual(normalDateAsDateObject);
            expect(component.value).not.toEqual(normalDate);
            expect(component.isInvalidDate).toBeFalse();
            expect(onChangeSpy).toHaveBeenCalledOnceWith(normalDateAsDateObject);
            expect(valueChangedStub).toHaveBeenCalledOnce();
        });

        it('should not update field with an invalid date object', () => {
            const valueChangedStub = jest.spyOn(component, 'valueChanged').mockImplementation();
            const onChangeSpy = jest.spyOn(component, '_onChange');
            component.value = normalDateAsDateObject;
            component.isInvalidDate = false;

            component.updateField(new Date('invalid string'));

            expect(component.value).toEqual(normalDateAsDateObject);
            expect(component.isInvalidDate).toBeFalse();
            expect(onChangeSpy).not.toHaveBeenCalled();
            expect(valueChangedStub).not.toHaveBeenCalled();
        });

        it('should not update field with object', () => {
            const valueChangedStub = jest.spyOn(component, 'valueChanged').mockImplementation();
            const onChangeSpy = jest.spyOn(component, '_onChange');
            component.value = normalDateAsDateObject;

            const newValue = {};
            component.updateField(newValue);

            expect(component.value).toBe(normalDateAsDateObject);
            expect(onChangeSpy).not.toHaveBeenCalled();
            expect(valueChangedStub).not.toHaveBeenCalled();
        });
    });

    describe('test update empty field', () => {
        it('updates value on empty field', () => {
            const valueChangedStub = jest.spyOn(component, 'valueChanged').mockImplementation();
            const onChangeSpy = jest.spyOn(component, '_onChange');
            component.value = normalDate;
            component.isInvalidDate = true;

            const inputValue = '';
            component.updateEmptyField(inputValue);

            expect(component.value).toBeNull();
            expect(component.isInvalidDate).toBeFalse();
            expect(onChangeSpy).toHaveBeenCalledOnceWith(null);
            expect(valueChangedStub).toHaveBeenCalledOnce();
        });

        it('not empty field stays unchanged', () => {
            const valueChangedStub = jest.spyOn(component, 'valueChanged').mockImplementation();
            const onChangeSpy = jest.spyOn(component, '_onChange');
            component.value = normalDate;
            component.isInvalidDate = true;

            const inputValue = 'a';
            component.updateEmptyField(inputValue);

            expect(component.value).toEqual(normalDate);
            expect(component.isInvalidDate).toBeTrue();
            expect(onChangeSpy).not.toHaveBeenCalled();
            expect(valueChangedStub).not.toHaveBeenCalled();
        });
    });

    describe('test update isValidDate', () => {
        it('checks if an object is a valid date', () => {
            expect(component.isValidDate(normalDateAsDateObject)).toBeTrue();
        });

        it('checks if an object is an invalid valid', () => {
            const invalidDate = new Date('some text, definitely not a date');
            expect(component.isValidDate(invalidDate)).toBeFalse();
        });
    });

    it('validateAndUpdateField gets called on input change', () => {
        const validateAndUpdateFieldStub = jest.spyOn(component, 'validateAndUpdateField').mockImplementation();
        fixture.debugElement.nativeElement.querySelector('#datePicker');

        const nativeElement = fixture.nativeElement;
        return fixture.whenStable().then(() => {
            const input = nativeElement.querySelector('input');

            input.dispatchEvent(new Event('change'));
            expect(validateAndUpdateFieldStub).toHaveBeenCalledOnce();
        });
    });

    it('validateAndUpdateField does not get called on input event', () => {
        const validateAndUpdateFieldStub = jest.spyOn(component, 'validateAndUpdateField').mockImplementation();
        const nativeElement = fixture.nativeElement;

        return fixture.whenStable().then(() => {
            const input = nativeElement.querySelector('input');
            input.dispatchEvent(new Event('input'));

            input.dispatchEvent(new Event('input'));
            expect(validateAndUpdateFieldStub).not.toHaveBeenCalled();
        });
    });
});
