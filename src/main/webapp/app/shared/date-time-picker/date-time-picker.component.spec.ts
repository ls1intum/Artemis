import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { DateTimePickerType, FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import dayjs from 'dayjs/esm';
import { MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from '../pipes/artemis-translate.pipe';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateModule } from '@ngx-translate/core';

describe('FormDateTimePickerComponent', () => {
    let component: FormDateTimePickerComponent;
    let fixture: ComponentFixture<FormDateTimePickerComponent>;

    beforeAll(() => {
        try {
            TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
        } catch {
            // The environment is already initialized when running through the Angular CLI test harness.
        }
    });

    const normalDate = dayjs('2022-01-02T22:15+00:00');
    const normalDateAsDateObject = new Date('2022-01-02T22:15+00:00');

    /**
     * Creates and appends a mock `.owl-dt-container-buttons` element to `document.body`,
     * simulating the owl-date-time popup's button row.
     */
    function createContainerButtons(): HTMLDivElement {
        const container = document.createElement('div');
        container.classList.add('owl-dt-container-buttons');
        const cancelButton = document.createElement('button');
        container.appendChild(cancelButton);
        document.body.appendChild(container);
        return container;
    }

    /**
     * Removes the container element from the DOM if it is still attached.
     */
    function removeContainerButtons(container: HTMLElement): void {
        if (container.parentNode) {
            container.parentNode.removeChild(container);
        }
    }

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(OwlDateTimeModule), MockPipe(ArtemisTranslatePipe), MockModule(NgbTooltipModule), MockDirective(TranslateDirective), TranslateModule.forRoot()],
            declarations: [FormDateTimePickerComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FormDateTimePickerComponent);
                component = fixture.componentInstance;
                component.dateInput = { reset: jest.fn() } as any;
            });
    });

    afterEach(() => {
        jest.clearAllTimers();
        jest.useRealTimers();
        fixture?.destroy();
        document.querySelectorAll('.owl-dt-container-buttons').forEach((el) => el.remove());
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

    it('should set the datepicker value to now', () => {
        const updateFieldSpy = jest.spyOn(component, 'updateField').mockImplementation();

        const beforeCall = dayjs();
        component.setNow();
        const afterCall = dayjs();

        expect(updateFieldSpy).toHaveBeenCalledOnce();
        const calledWith = updateFieldSpy.mock.calls[0][0];
        expect(calledWith.isAfter(beforeCall.subtract(1, 'second'))).toBeTrue();
        expect(calledWith.isBefore(afterCall.add(1, 'second'))).toBeTrue();
    });

    it('should inject a Now button into the picker popup on open', () => {
        jest.useFakeTimers();
        const containerButtons = createContainerButtons();

        const mockPicker = { close: jest.fn() } as any;
        component.onPickerOpen(mockPicker);
        jest.runAllTimers();

        const nowButton = containerButtons.querySelector('.owl-dt-now-button');
        expect(nowButton).toBeTruthy();
        expect(nowButton).toBe(containerButtons.firstChild);
        expect(nowButton?.getAttribute('title')).toBeTruthy();
        expect(nowButton?.getAttribute('aria-label')).toBeTruthy();
        expect(nowButton?.getAttribute('title')).toBe(nowButton?.getAttribute('aria-label'));

        component.onPickerClose();
        removeContainerButtons(containerButtons);
        jest.useRealTimers();
    });

    it('should select current date and confirm when Now button is clicked', () => {
        jest.useFakeTimers();
        const containerButtons = createContainerButtons();

        const mockPicker = { select: jest.fn(), confirmSelect: jest.fn() } as any;

        component.onPickerOpen(mockPicker);
        jest.runAllTimers();

        const beforeClick = new Date();
        const nowButton = containerButtons.querySelector('.owl-dt-now-button') as HTMLButtonElement;
        nowButton.click();
        const afterClick = new Date();

        expect(mockPicker.select).toHaveBeenCalledOnce();
        const selectedDate = mockPicker.select.mock.calls[0][0] as Date;
        expect(selectedDate.getTime()).toBeGreaterThanOrEqual(beforeClick.getTime());
        expect(selectedDate.getTime()).toBeLessThanOrEqual(afterClick.getTime());
        expect(mockPicker.confirmSelect).toHaveBeenCalledOnce();

        component.onPickerClose();
        removeContainerButtons(containerButtons);
        jest.useRealTimers();
    });

    it('should clean up timeout and button on destroy', () => {
        jest.useFakeTimers();
        const containerButtons = createContainerButtons();

        const mockPicker = { close: jest.fn() } as any;
        component.onPickerOpen(mockPicker);

        // Destroy before setTimeout fires
        component.ngOnDestroy();
        jest.runAllTimers();

        // The Now button should NOT have been injected since the timeout was cleared
        expect(containerButtons.querySelector('.owl-dt-now-button')).toBeFalsy();

        removeContainerButtons(containerButtons);
        jest.useRealTimers();
    });

    it('should clean up existing button on destroy after timeout has fired', () => {
        jest.useFakeTimers();
        const containerButtons = createContainerButtons();

        const mockPicker = { close: jest.fn() } as any;
        component.onPickerOpen(mockPicker);
        jest.runAllTimers();

        expect(containerButtons.querySelector('.owl-dt-now-button')).toBeTruthy();

        component.ngOnDestroy();

        expect(containerButtons.querySelector('.owl-dt-now-button')).toBeFalsy();

        removeContainerButtons(containerButtons);
        jest.useRealTimers();
    });

    it('should clear pending timeout on picker close before it fires', () => {
        jest.useFakeTimers();
        const containerButtons = createContainerButtons();

        const mockPicker = { close: jest.fn() } as any;
        component.onPickerOpen(mockPicker);

        // Close before setTimeout fires
        component.onPickerClose();
        jest.runAllTimers();

        // The Now button should NOT have been injected since the timeout was cleared by close
        expect(containerButtons.querySelector('.owl-dt-now-button')).toBeFalsy();

        removeContainerButtons(containerButtons);
        jest.useRealTimers();
    });

    it('should remove the Now button on picker close', () => {
        jest.useFakeTimers();
        const containerButtons = createContainerButtons();

        const mockPicker = { close: jest.fn() } as any;
        component.onPickerOpen(mockPicker);
        jest.runAllTimers();

        expect(containerButtons.querySelector('.owl-dt-now-button')).toBeTruthy();

        component.onPickerClose();

        expect(containerButtons.querySelector('.owl-dt-now-button')).toBeFalsy();

        removeContainerButtons(containerButtons);
        jest.useRealTimers();
    });

    it('should not inject Now button when disabled', () => {
        jest.useFakeTimers();
        const containerButtons = createContainerButtons();

        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();

        const mockPicker = { close: jest.fn() } as any;
        component.onPickerOpen(mockPicker);
        jest.runAllTimers();

        expect(containerButtons.querySelector('.owl-dt-now-button')).toBeFalsy();

        removeContainerButtons(containerButtons);
        jest.useRealTimers();
    });

    it('should not inject Now button for non-DEFAULT picker types', () => {
        jest.useFakeTimers();
        const containerButtons = createContainerButtons();

        fixture.componentRef.setInput('pickerType', DateTimePickerType.CALENDAR);
        fixture.detectChanges();

        const mockPicker = { close: jest.fn() } as any;
        component.onPickerOpen(mockPicker);
        jest.runAllTimers();

        expect(containerButtons.querySelector('.owl-dt-now-button')).toBeFalsy();

        removeContainerButtons(containerButtons);
        jest.useRealTimers();
    });
});
