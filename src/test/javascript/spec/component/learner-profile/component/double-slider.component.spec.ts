import { DoubleSliderComponent } from 'app/shared/double-slider/double-slider.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';

describe('DoubleSliderComponent', () => {
    let component: DoubleSliderComponent;
    let fixture: ComponentFixture<DoubleSliderComponent>;

    const initialValue = 1;
    const currentValue = signal(2);
    const min = 1;
    const max = 5;
    const title = 'Test title';

    beforeEach(async () => {
        await TestBed.configureTestingModule({}).compileComponents();

        fixture = TestBed.createComponent(DoubleSliderComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('currentValue', currentValue);
        fixture.componentRef.setInput('initialValue', initialValue);
        fixture.componentRef.setInput('min', min);
        fixture.componentRef.setInput('max', max);
        fixture.componentRef.setInput('title', title);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.max()).toEqual(max);
        expect(component.min()).toEqual(min);
        expect(component.initialValue()).toEqual(initialValue);
        expect(component.currentValue()).toEqual(currentValue);
    });

    it('should update signal after change', async () => {
        let val = 2;
        fixture.nativeElement.querySelector('#currentSlider').value = val;

        fixture.detectChanges();
        await fixture.whenStable();

        expect(currentValue()).toBe(val);
    });
});
