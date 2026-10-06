import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { RangeSliderComponent } from 'app/shared-ui/range-slider/range-slider.component';

describe('RangeSliderComponent', () => {
    let component: RangeSliderComponent;
    let fixture: ComponentFixture<RangeSliderComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [RangeSliderComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(RangeSliderComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('generalMinValue', 0);
        fixture.componentRef.setInput('generalMaxValue', 100);
        fixture.componentRef.setInput('selectedMinValue', 20);
        fixture.componentRef.setInput('selectedMaxValue', 80);
        fixture.componentRef.setInput('step', 5);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('gives each range thumb a distinct translated accessible name', () => {
        vi.spyOn(TestBed.inject(TranslateService), 'instant').mockImplementation((key) =>
            key === 'accessibility.rangeMinimum' ? 'Minimum selected value' : 'Maximum selected value',
        );
        fixture.detectChanges();

        const inputs: NodeListOf<HTMLInputElement> = fixture.nativeElement.querySelectorAll('input[type="range"]');
        expect(inputs[0].getAttribute('aria-label')).toBe('Minimum selected value');
        expect(inputs[1].getAttribute('aria-label')).toBe('Maximum selected value');
    });

    it('should emit the updated max value on (change)', () => {
        const emitSpy = vi.spyOn(component.selectedMaxValueChange, 'emit');

        // Simulate the slider being dragged to 90 — local mirror is updated by ngModelChange.
        component['localMaxValue'].set(90);
        const event = new Event('change');
        Object.defineProperty(event, 'target', { value: { className: 'range-max', value: 90 } });

        component.onSelectedMaxValueChanged(event);
        expect(emitSpy).toHaveBeenCalledWith(90);
    });

    it('should emit the updated max value rounded up to the next selectable value', () => {
        const emitSpy = vi.spyOn(component.selectedMaxValueChange, 'emit');

        component['localMaxValue'].set(11);
        const event = new Event('change');
        Object.defineProperty(event, 'target', { value: { className: 'range-max', value: 11 } });

        component.onSelectedMaxValueChanged(event);
        expect(emitSpy).toHaveBeenCalledWith(25);
    });

    it('should emit the updated min value on (change)', () => {
        const emitSpy = vi.spyOn(component.selectedMinValueChange, 'emit');

        component['localMinValue'].set(30);
        const event = new Event('change');
        Object.defineProperty(event, 'target', { value: { className: 'range-min', value: 30 } });

        component.onSelectedMinValueChanged(event);
        expect(emitSpy).toHaveBeenCalledWith(30);
    });

    it('should emit the updated min value rounded down to the next selectable value', () => {
        const emitSpy = vi.spyOn(component.selectedMinValueChange, 'emit');

        component['localMinValue'].set(99);
        const event = new Event('change');
        Object.defineProperty(event, 'target', { value: { className: 'range-min', value: 99 } });

        component.onSelectedMinValueChanged(event);
        expect(emitSpy).toHaveBeenCalledWith(75);
    });

    it('should mirror parent input changes into the local state', () => {
        fixture.componentRef.setInput('selectedMaxValue', 60);
        fixture.detectChanges();
        expect(component['localMaxValue']()).toBe(60);
    });

    it('clamps the min thumb in the DOM when dragged past the max', () => {
        const minInput: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('input.range-min');
        // Simulate the browser moving the thumb beyond the max selection during a drag.
        minInput.value = '90';
        minInput.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        // selectedMaxValue is 80, step 5 -> min must be clamped to 75 both in state and in the DOM.
        expect(component['localMinValue']()).toBe(75);
        expect(minInput.value).toBe('75');
    });

    it('clamps the max thumb in the DOM when dragged below the min', () => {
        const maxInput: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('input.range-max');
        maxInput.value = '10';
        maxInput.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        // selectedMinValue is 20, step 5 -> max must be clamped to 25 both in state and in the DOM.
        expect(component['localMaxValue']()).toBe(25);
        expect(maxInput.value).toBe('25');
    });
});
