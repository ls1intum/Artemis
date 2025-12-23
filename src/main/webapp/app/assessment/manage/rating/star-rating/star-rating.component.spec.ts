import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { StarRatingComponent } from 'app/assessment/manage/rating/star-rating/star-rating.component';

describe('StarRatingComponent', () => {
    let component: StarRatingComponent;
    let fixture: ComponentFixture<StarRatingComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({}).compileComponents();
        fixture = TestBed.createComponent(StarRatingComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    // =========================================================================
    // Component Creation
    // =========================================================================

    describe('component creation', () => {
        it('should create the component', () => {
            expect(component).toBeTruthy();
        });

        it('should render 5 stars by default', () => {
            expect(component.totalStars).toBe(5);
        });
    });

    // =========================================================================
    // Default Values
    // =========================================================================

    describe('default values', () => {
        it('should have default totalStars of 5', () => {
            expect(component.totalStars).toBe(5);
        });

        it('should have default value of 0', () => {
            expect(component.value).toBe(0);
        });

        it('should have default size of 24px', () => {
            expect(component.size).toBe('24px');
        });

        it('should not be read-only by default', () => {
            expect(component.readOnly).toBeFalse();
        });

        it('should have undefined checkedColor by default', () => {
            expect(component.checkedColor()).toBeUndefined();
        });

        it('should have undefined uncheckedColor by default', () => {
            expect(component.uncheckedColor()).toBeUndefined();
        });
    });

    // =========================================================================
    // Value Handling
    // =========================================================================

    describe('value handling', () => {
        it('should allow setting value programmatically', () => {
            component.value = 3;
            expect(component.value).toBe(3);
        });

        it('should normalize undefined value to 0', () => {
            component.value = undefined as unknown as number;
            expect(component.value).toBe(0);
        });

        it('should normalize null value to 0', () => {
            component.value = null as unknown as number;
            expect(component.value).toBe(0);
        });

        it('should accept decimal values for half-star display', () => {
            component.value = 3.5;
            expect(component.value).toBe(3.5);
        });

        it('should accept value of 0', () => {
            component.value = 0;
            expect(component.value).toBe(0);
        });
    });

    // =========================================================================
    // Size Handling
    // =========================================================================

    describe('size handling', () => {
        it('should return size with px suffix when provided without', () => {
            fixture.componentRef.setInput('size', '32');
            fixture.detectChanges();

            expect(component.size).toBe('32px');
        });

        it('should preserve px suffix when already provided', () => {
            fixture.componentRef.setInput('size', '48px');
            fixture.detectChanges();

            expect(component.size).toBe('48px');
        });

        it('should default to 24px when size is empty', () => {
            fixture.componentRef.setInput('size', '');
            fixture.detectChanges();

            expect(component.size).toBe('24px');
        });
    });

    // =========================================================================
    // TotalStars Handling
    // =========================================================================

    describe('totalStars handling', () => {
        it('should normalize negative totalStars to 5', () => {
            fixture.componentRef.setInput('totalStars', -1);
            fixture.detectChanges();

            expect(component.totalStars).toBe(5);
        });

        it('should normalize zero totalStars to 5', () => {
            fixture.componentRef.setInput('totalStars', 0);
            fixture.detectChanges();

            expect(component.totalStars).toBe(5);
        });

        it('should round decimal totalStars to nearest integer', () => {
            fixture.componentRef.setInput('totalStars', 3.7);
            fixture.detectChanges();

            expect(component.totalStars).toBe(4);
        });

        it('should accept custom totalStars value', () => {
            fixture.componentRef.setInput('totalStars', 10);
            fixture.detectChanges();

            expect(component.totalStars).toBe(10);
        });
    });

    // =========================================================================
    // Read-Only Mode
    // =========================================================================

    describe('read-only mode', () => {
        it('should respect readOnly input', () => {
            fixture.componentRef.setInput('readOnly', true);
            fixture.detectChanges();

            expect(component.readOnly).toBeTrue();
        });

        it('should be editable when readOnly is false', () => {
            fixture.componentRef.setInput('readOnly', false);
            fixture.detectChanges();

            expect(component.readOnly).toBeFalse();
        });
    });

    // =========================================================================
    // Color Inputs
    // =========================================================================

    describe('color inputs', () => {
        it('should accept checkedColor input', () => {
            fixture.componentRef.setInput('checkedColor', 'gold');
            fixture.detectChanges();

            expect(component.checkedColor()).toBe('gold');
        });

        it('should accept uncheckedColor input', () => {
            fixture.componentRef.setInput('uncheckedColor', 'gray');
            fixture.detectChanges();

            expect(component.uncheckedColor()).toBe('gray');
        });

        it('should accept hex color values', () => {
            fixture.componentRef.setInput('checkedColor', '#FFD700');
            fixture.detectChanges();

            expect(component.checkedColor()).toBe('#FFD700');
        });
    });

    // =========================================================================
    // Rating Output
    // =========================================================================

    describe('rate output', () => {
        it('should be defined', () => {
            expect(component.rate).toBeDefined();
        });
    });

    // =========================================================================
    // Input Changes via setInput
    // =========================================================================

    describe('input changes', () => {
        it('should update value when input changes', fakeAsync(() => {
            fixture.componentRef.setInput('value', 4);
            fixture.detectChanges();
            tick();

            expect(component.value).toBe(4);
        }));

        it('should update totalStars when input changes', fakeAsync(() => {
            fixture.componentRef.setInput('totalStars', 7);
            fixture.detectChanges();
            tick();

            expect(component.totalStars).toBe(7);
        }));
    });
});
