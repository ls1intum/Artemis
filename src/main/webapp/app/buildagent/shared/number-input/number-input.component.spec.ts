import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NumberInputComponent } from './number-input.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { CommonModule } from '@angular/common';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';

describe('NumberInputComponent', () => {
    let component: NumberInputComponent;
    let fixture: ComponentFixture<NumberInputComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [NumberInputComponent, FontAwesomeModule, CommonModule, NgbModule],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(NumberInputComponent);
        component = fixture.componentInstance;

        // Set up required inputs before detectChanges
        fixture.componentRef.setInput('value', 5);
        fixture.componentRef.setInput('minValue', 1);
        fixture.componentRef.setInput('maxValue', 10);
        fixture.componentRef.setInput('disabled', false);

        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should increment value when increment is called', () => {
        const valueChangeSpy = jest.spyOn(component.valueChange, 'emit');
        const initialValue = component.value();

        component.increment();

        expect(valueChangeSpy).toHaveBeenCalledWith(initialValue + 1);
    });

    it('should not increment when value is at maxValue', () => {
        fixture.componentRef.setInput('value', 10);
        const valueChangeSpy = jest.spyOn(component.valueChange, 'emit');

        component.increment();

        expect(valueChangeSpy).not.toHaveBeenCalled();
    });

    it('should not increment when component is disabled', () => {
        fixture.componentRef.setInput('disabled', true);
        const valueChangeSpy = jest.spyOn(component.valueChange, 'emit');

        component.increment();

        expect(valueChangeSpy).not.toHaveBeenCalled();
    });

    it('should decrement value when decrement is called', () => {
        const valueChangeSpy = jest.spyOn(component.valueChange, 'emit');
        const initialValue = component.value();

        component.decrement();

        expect(valueChangeSpy).toHaveBeenCalledWith(initialValue - 1);
    });

    it('should not decrement when value is at minValue', () => {
        fixture.componentRef.setInput('value', 1);
        const valueChangeSpy = jest.spyOn(component.valueChange, 'emit');

        component.decrement();

        expect(valueChangeSpy).not.toHaveBeenCalled();
    });

    it('should not decrement when component is disabled', () => {
        fixture.componentRef.setInput('disabled', true);
        const valueChangeSpy = jest.spyOn(component.valueChange, 'emit');

        component.decrement();

        expect(valueChangeSpy).not.toHaveBeenCalled();
    });

    it('should handle valid input on blur', () => {
        const valueChangeSpy = jest.spyOn(component.valueChange, 'emit');
        const mockEvent = {
            target: { value: '7' },
        } as unknown as Event;

        component.onInputBlur(mockEvent);

        expect(valueChangeSpy).toHaveBeenCalledWith(7);
    });

    it('should handle invalid inputs on blur', () => {
        const invalidInputs = ['invalid', '', 'abc123'];

        invalidInputs.forEach((input) => {
            const valueChangeSpy = jest.spyOn(component.valueChange, 'emit');
            const mockEvent = {
                target: { value: input },
            } as unknown as Event;

            component.onInputBlur(mockEvent);

            expect(valueChangeSpy).not.toHaveBeenCalled();
            expect((mockEvent.target as HTMLInputElement).value).toBe('5');
        });
    });

    it('should handle input below minValue on blur', () => {
        const valueChangeSpy = jest.spyOn(component.valueChange, 'emit');
        const mockEvent = {
            target: { value: '0' },
        } as unknown as Event;

        component.onInputBlur(mockEvent);

        expect(valueChangeSpy).toHaveBeenCalledWith(1);
        expect((mockEvent.target as HTMLInputElement).value).toBe('1');
    });

    it('should handle input above maxValue on blur', () => {
        const valueChangeSpy = jest.spyOn(component.valueChange, 'emit');
        const mockEvent = {
            target: { value: '15' },
        } as unknown as Event;

        component.onInputBlur(mockEvent);

        expect(valueChangeSpy).toHaveBeenCalledWith(10);
        expect((mockEvent.target as HTMLInputElement).value).toBe('10');
    });

    it('should not emit when input value equals current value on blur', () => {
        const valueChangeSpy = jest.spyOn(component.valueChange, 'emit');
        const mockEvent = {
            target: { value: '5' },
        } as unknown as Event;

        component.onInputBlur(mockEvent);

        expect(valueChangeSpy).not.toHaveBeenCalled();
    });

    it('should render increment and decrement buttons', () => {
        const compiled = fixture.nativeElement;
        const incrementButton = compiled.querySelector('button[aria-label="Increase value"]');
        const decrementButton = compiled.querySelector('button[aria-label="Decrease value"]');
        const input = compiled.querySelector('input[type="number"]');

        expect(incrementButton).toBeTruthy();
        expect(decrementButton).toBeTruthy();
        expect(input).toBeTruthy();
        expect(input.value).toBe('5');
    });

    it('should hide buttons when component is disabled', () => {
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        const incrementButton = compiled.querySelector('button[aria-label="Increase value"]');
        const decrementButton = compiled.querySelector('button[aria-label="Decrease value"]');
        const input = compiled.querySelector('input[type="number"]');

        expect(incrementButton).toBeNull();
        expect(decrementButton).toBeNull();
        expect(input.disabled).toBeTrue();
    });

    it('should show tooltip when disabled', () => {
        fixture.componentRef.setInput('disabled', true);
        fixture.componentRef.setInput('disabledTooltip', 'Test tooltip');
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        const wrapper = compiled.querySelector('.number-input-wrapper');

        expect(wrapper).not.toBeNull();
        expect(component.disabledTooltip()).toBe('Test tooltip');
    });

    it('should not show tooltip when not disabled', () => {
        fixture.componentRef.setInput('disabled', false);
        fixture.componentRef.setInput('disabledTooltip', 'Test tooltip');
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        const wrapper = compiled.querySelector('.number-input-wrapper');

        expect(wrapper).not.toBeNull();
        expect(component.disabledTooltip()).toBe('Test tooltip');
    });

    it('should disable buttons when at min/max values', () => {
        fixture.componentRef.setInput('value', 1);
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        const incrementButton = compiled.querySelector('button[aria-label="Increase value"]');
        const decrementButton = compiled.querySelector('button[aria-label="Decrease value"]');

        expect(incrementButton.disabled).toBeFalse();
        expect(decrementButton.disabled).toBeTrue();

        // Test increment button disabled when at max value
        fixture.componentRef.setInput('value', 10); // Set to max value
        fixture.detectChanges();

        expect(incrementButton.disabled).toBeTrue();
        expect(decrementButton.disabled).toBeFalse();
    });
});
