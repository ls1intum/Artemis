import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RangeSliderComponent } from 'app/shared/range-slider/range-slider.component';

describe('RangeSliderComponent', () => {
    setupTestBed({ zoneless: true });

    let component: RangeSliderComponent;
    let fixture: ComponentFixture<RangeSliderComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [RangeSliderComponent],
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
});
