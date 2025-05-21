import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SegmentedToggleComponent } from './segmented-toggle.component';

describe('SegmentedToggleComponent', () => {
    let component: SegmentedToggleComponent;
    let fixture: ComponentFixture<SegmentedToggleComponent>;

    const mockOptions = [
        { label: 'Option 1', value: 'opt1' },
        { label: 'Option 2', value: 'opt2' },
        { label: 'Option 3', value: 'opt3' },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SegmentedToggleComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(SegmentedToggleComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with empty options array', () => {
        expect(component.options).toEqual([]);
    });

    it('should bind options correctly', () => {
        component.options = mockOptions;
        fixture.detectChanges();
        expect(component.options).toEqual(mockOptions);
    });

    it('should bind selected value correctly', () => {
        component.selected = 'opt2';
        fixture.detectChanges();
        expect(component.selected).toBe('opt2');
    });

    it('should emit selectedChange event when an option is selected', () => {
        const selectedValue = 'opt1';
        const spy = jest.spyOn(component.selectedChange, 'emit');

        component.select(selectedValue);

        expect(spy).toHaveBeenCalledWith(selectedValue);
        expect(component.selected).toBe(selectedValue);
    });

    it('should handle empty options array', () => {
        component.options = [];
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        const options = compiled.querySelectorAll('.segmented-toggle-option');
        expect(options).toHaveLength(0);
    });

    it('should render all options correctly', () => {
        component.options = mockOptions;
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        const options = compiled.querySelectorAll('.btn');
        expect(options).toHaveLength(mockOptions.length);

        mockOptions.forEach((option, index) => {
            expect(options[index].textContent.trim()).toBe(option.label);
        });
    });

    it('should apply selected class to the active option', () => {
        component.options = mockOptions;
        component.selected = 'opt2';
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        const selectedOption = compiled.querySelector('.btn-primary.selected');
        expect(selectedOption).toBeTruthy();
        expect(selectedOption.textContent.trim()).toBe('Option 2');
    });
});
