import { RangeSliderComponent } from 'app/shared/range-slider/range-slider.component';

describe('RangeSliderComponent', () => {
    let component: RangeSliderComponent;

    beforeEach(() => {
        component = new RangeSliderComponent();
        component.generalMinValue = 0;
        component.generalMaxValue = 100;
        component.selectedMinValue = 20;
        component.selectedMaxValue = 80;
        component.step = 5;
    });

    it('should emit the updated max value', () => {
        const emitSpy = jest.spyOn(component.selectedMaxValueChange, 'emit');

        component.selectedMaxValue = 90;
        const event = new Event('input');
        Object.defineProperty(event, 'target', { value: { className: 'range-max', value: 90 } });

        component.onSelectedMaxValueChanged(event);
        expect(emitSpy).toHaveBeenCalledWith(90);
    });

    it('should emit the updated max value rounded up to next selectable value', () => {
        const emitSpy = jest.spyOn(component.selectedMaxValueChange, 'emit');

        component.selectedMaxValue = 11;
        const event = new Event('input');
        Object.defineProperty(event, 'target', { value: { className: 'range-max', value: 11 } });

        component.onSelectedMaxValueChanged(event);
        expect(emitSpy).toHaveBeenCalledWith(25);
    });

    it('should emit the updated min value', () => {
        const emitSpy = jest.spyOn(component.selectedMinValueChange, 'emit');

        component.selectedMinValue = 30;
        const event = new Event('input');
        Object.defineProperty(event, 'target', { value: { className: 'range-min', value: 30 } });

        component.onSelectedMinValueChanged(event);
        expect(emitSpy).toHaveBeenCalledWith(30);
    });

    it('should emit the updated min value rounded down to next selectable value', () => {
        const emitSpy = jest.spyOn(component.selectedMinValueChange, 'emit');

        component.selectedMinValue = 99;
        const event = new Event('input');
        Object.defineProperty(event, 'target', { value: { className: 'range-min', value: 99 } });

        component.onSelectedMinValueChanged(event);
        expect(emitSpy).toHaveBeenCalledWith(75);
    });
});
