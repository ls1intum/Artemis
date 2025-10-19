import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SegmentedToggleComponent } from './segmented-toggle.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';

describe('SegmentedToggleComponent', () => {
    let component: SegmentedToggleComponent<number>;
    let fixture: ComponentFixture<SegmentedToggleComponent<number>>;

    const mockOptions = [
        { label: 'Option 1', value: 1 },
        { label: 'Option 2', value: 2 },
        { label: 'Option 3', value: 3 },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SegmentedToggleComponent, TranslateModule.forRoot()],
            providers: [provideHttpClient(), provideHttpClientTesting()],
        }).compileComponents();

        fixture = TestBed.createComponent(SegmentedToggleComponent<number>);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('options', mockOptions);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
        expect(component.options()).toEqual(mockOptions);
    });

    it('should bind selected value correctly', () => {
        component.selected.set(2);
        fixture.detectChanges();
        expect(component.selected()).toBe(2);
    });

    it('should emit selectedChange event when an option is selected', () => {
        const selectedValue = 1;
        component.select(selectedValue);
        expect(component.selected()).toBe(selectedValue);
    });

    it('should handle empty options array', () => {
        fixture.componentRef.setInput('options', []);
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        const options = compiled.querySelectorAll('jhi-button');
        expect(options).toHaveLength(0);
    });

    it('should render all options correctly', () => {
        fixture.componentRef.setInput('options', mockOptions);
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        const options = compiled.querySelectorAll('button');
        expect(options).toHaveLength(mockOptions.length);

        options.forEach((option: HTMLElement, index: number) => {
            expect(option.textContent?.trim()).toBe(mockOptions[index].label);
        });
    });

    it('should apply selected class to the active option', () => {
        fixture.componentRef.setInput('options', mockOptions);
        component.selected.set(2);
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        const selectedOption = compiled.querySelector('.btn-primary');
        expect(selectedOption).toBeTruthy();
        expect(selectedOption.textContent.trim()).toBe('Option 2');
    });
});
