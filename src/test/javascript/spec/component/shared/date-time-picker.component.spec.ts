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

        component.valueChanged();

        expect(emitStub).toHaveBeenCalledOnce();
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

            expect(unconvertedDate.isValid()).toBeFalse();

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

    it('should update field', () => {
        const valueChangedStub = jest.spyOn(component, 'valueChanged').mockImplementation();
        const onChangeSpy = jest.spyOn(component, '_onChange');
        const newDate = normalDate.add(2, 'days');
        component.value = normalDate;

        component.updateField(newDate);

        expect(component.value).toEqual(newDate);
        expect(onChangeSpy).toHaveBeenCalledOnce();
        expect(onChangeSpy).toHaveBeenCalledWith(newDate);
        expect(valueChangedStub).toHaveBeenCalledOnce();
    });
});
