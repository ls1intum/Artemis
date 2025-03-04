import { DoubleSliderComponent } from 'app/shared/editable-slider/double-slider.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { EditStateTransition } from 'app/shared/editable-slider/edit-process.component';

describe('DoubleSliderComponent', () => {
    let component: DoubleSliderComponent;
    let fixture: ComponentFixture<DoubleSliderComponent>;

    const editStateTransition = signal(EditStateTransition.Abort);
    const currentValue = 1;
    const initialValue = signal(2);
    const min = 0;
    const max = 5;

    beforeEach(async () => {
        await TestBed.configureTestingModule({}).compileComponents();

        fixture = TestBed.createComponent(DoubleSliderComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('editStateTransition', editStateTransition);
        fixture.componentRef.setInput('currentValue', currentValue);
        fixture.componentRef.setInput('initialValue', initialValue);
        fixture.componentRef.setInput('min', min);
        fixture.componentRef.setInput('max', max);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.max()).toEqual(max);
        expect(component.min()).toEqual(min);
        expect(component.editStateTransition()).toEqual(editStateTransition);
        expect(component.initialValue()).toEqual(initialValue);
        expect(component.currentValue()).toEqual(currentValue);
    });

    it('should enable slider when editing', () => {
        component.onEdit();
        expect(component.currentSlider().nativeElement.disabled).toBeFalse();
    });

    it('should reset slider after abort', () => {
        component.onEdit();
        component.currentSlider().nativeElement.value = ((currentValue + 1) % (max - min)) + min;
        component.onAbort();
        expect(Number(component.currentSlider().nativeElement.value)).toBe(currentValue);
    });

    it('should update initial value on try save', () => {
        component.onEdit();
        const newVal = ((currentValue + 1) % (max - min)) + min;
        component.currentSlider().nativeElement.value = newVal;
        component.onTrySave();
        expect(initialValue()).toBe(newVal);
    });

    it('should reset after abort after save', () => {
        component.onEdit();
        const oldValue = initialValue();
        component.currentSlider().nativeElement.value = ((currentValue + 1) % (max - min)) + min;
        component.onTrySave();
        component.onAbort();
        expect(initialValue()).toBe(oldValue);
        expect(Number(component.currentSlider().nativeElement.value)).toBe(currentValue);
    });

    it('should persist state on final save', () => {
        component.onEdit();
        const newVal = ((currentValue + 1) % (max - min)) + min;
        component.currentSlider().nativeElement.value = newVal;
        component.onTrySave();
        component.onSaved();

        expect(initialValue()).toBe(newVal);
        expect(Number(component.currentSlider().nativeElement.value)).toBe(newVal);
    });
});
