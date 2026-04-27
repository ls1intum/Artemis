import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import dayjs from 'dayjs/esm';
import { MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from '../pipes/artemis-translate.pipe';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';

describe('FormDateTimePickerComponent', () => {
    let component: FormDateTimePickerComponent;
    let fixture: ComponentFixture<FormDateTimePickerComponent>;

    const normalDate = dayjs('2022-01-02T22:15+00:00');
    const normalDateAsDateObject = new Date('2022-01-02T22:15+00:00');

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(OwlDateTimeModule), MockPipe(ArtemisTranslatePipe), MockModule(NgbTooltipModule)],
            declarations: [FormDateTimePickerComponent],
            providers: [MockProvider(TranslateService, { instant: jest.fn((key: string) => key) })],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FormDateTimePickerComponent);
                component = fixture.componentInstance;
                component.dateInput = { reset: jest.fn() } as any;
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
            convertedDate = component.convertToDate(normalDate);

            expect(convertedDate).toEqual(normalDateAsDateObject);
        });

        it('should return null if dayjs is undefined', () => {
            convertedDate = component.convertToDate();

            expect(convertedDate).toBeNull();
        });

        it('should return null if dayjs is invalid', () => {
            const unconvertedDate = dayjs('2022-31-02T00:00+00:00');

            expect(unconvertedDate.isValid()).toBeFalse();

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
        const onChangeSpy = jest.fn();
        component.registerOnChange(onChangeSpy);

        (component as any).onChange?.(normalDate);

        expect(onChangeSpy).toHaveBeenCalledOnce();
        expect(onChangeSpy).toHaveBeenCalledWith(normalDate);
    });

    it('should update field', () => {
        const onChangeSpy = jest.fn();
        component.registerOnChange(onChangeSpy);
        const valueChangedStub = jest.spyOn(component, 'valueChanged').mockImplementation();
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
        const resetSpy = jest.spyOn(component.dateInput, 'reset').mockImplementation();
        const updateSignalsSpy = jest.spyOn(component, 'updateSignals').mockImplementation();

        component.clearDate();

        expect(resetSpy).toHaveBeenCalledWith(undefined);
        expect(updateSignalsSpy).toHaveBeenCalled();
    });

    describe('Now button', () => {
        it('should call select and confirmSelect on the picker when setNow is called', () => {
            const mockPicker = { select: jest.fn(), confirmSelect: jest.fn() };
            jest.spyOn(component as any, 'dtDefault').mockReturnValue(mockPicker);

            component.setNow();

            expect(mockPicker.select).toHaveBeenCalledWith(expect.any(Date));
            expect(mockPicker.confirmSelect).toHaveBeenCalledOnce();
        });

        it('should clamp to minDate when now is before min', () => {
            const futureMin = new Date(Date.now() + 3600000);
            const mockPicker = { select: jest.fn(), confirmSelect: jest.fn() };
            jest.spyOn(component as any, 'dtDefault').mockReturnValue(mockPicker);
            jest.spyOn(component, 'minDate').mockReturnValue(futureMin);
            jest.spyOn(component, 'maxDate').mockReturnValue(null);

            component.setNow();

            expect(mockPicker.select).toHaveBeenCalledWith(futureMin);
            expect(mockPicker.confirmSelect).toHaveBeenCalledOnce();
        });

        it('should clamp to maxDate when now is after max', () => {
            const pastMax = new Date(Date.now() - 3600000);
            const mockPicker = { select: jest.fn(), confirmSelect: jest.fn() };
            jest.spyOn(component as any, 'dtDefault').mockReturnValue(mockPicker);
            jest.spyOn(component, 'minDate').mockReturnValue(null);
            jest.spyOn(component, 'maxDate').mockReturnValue(pastMax);

            component.setNow();

            expect(mockPicker.select).toHaveBeenCalledWith(pastMax);
            expect(mockPicker.confirmSelect).toHaveBeenCalledOnce();
        });

        it('should not throw when setNow is called without a picker', () => {
            jest.spyOn(component as any, 'dtDefault').mockReturnValue(undefined);

            expect(() => component.setNow()).not.toThrow();
        });

        it('should inject a Now button into the container on picker open', fakeAsync(() => {
            const buttonsContainer = document.createElement('div');
            buttonsContainer.classList.add('owl-dt-container-buttons');
            const setButton = document.createElement('button');
            buttonsContainer.appendChild(setButton);
            document.body.appendChild(buttonsContainer);

            component.onPickerOpen();
            tick();

            const nowButton = buttonsContainer.querySelector('button:first-child');
            expect(nowButton).toBeDefined();
            expect(nowButton?.getAttribute('aria-label')).toBe('entity.now');
            expect(buttonsContainer.children).toHaveLength(2);

            // Cleanup
            component.onPickerClosed();
            document.body.removeChild(buttonsContainer);
        }));

        it('should remove the Now button on picker close', fakeAsync(() => {
            const buttonsContainer = document.createElement('div');
            buttonsContainer.classList.add('owl-dt-container-buttons');
            const setButton = document.createElement('button');
            buttonsContainer.appendChild(setButton);
            document.body.appendChild(buttonsContainer);

            component.onPickerOpen();
            tick();

            expect(buttonsContainer.children).toHaveLength(2);

            component.onPickerClosed();

            expect(buttonsContainer.children).toHaveLength(1);

            document.body.removeChild(buttonsContainer);
        }));

        it('should clear timeout when picker closes before button injection', fakeAsync(() => {
            const buttonsContainer = document.createElement('div');
            buttonsContainer.classList.add('owl-dt-container-buttons');
            const setButton = document.createElement('button');
            buttonsContainer.appendChild(setButton);
            document.body.appendChild(buttonsContainer);

            component.onPickerOpen();
            // Close immediately before setTimeout fires
            component.onPickerClosed();
            tick();

            // Button should not have been injected
            expect(buttonsContainer.children).toHaveLength(1);

            document.body.removeChild(buttonsContainer);
        }));

        it('should call setNow when the Now button is clicked', fakeAsync(() => {
            const buttonsContainer = document.createElement('div');
            buttonsContainer.classList.add('owl-dt-container-buttons');
            const setButton = document.createElement('button');
            buttonsContainer.appendChild(setButton);
            document.body.appendChild(buttonsContainer);

            const setNowSpy = jest.spyOn(component, 'setNow').mockImplementation();

            component.onPickerOpen();
            tick();

            const nowButton = buttonsContainer.querySelector('button:first-child') as HTMLButtonElement;
            nowButton.click();

            expect(setNowSpy).toHaveBeenCalledOnce();

            // Cleanup
            component.onPickerClosed();
            document.body.removeChild(buttonsContainer);
        }));
    });
});
