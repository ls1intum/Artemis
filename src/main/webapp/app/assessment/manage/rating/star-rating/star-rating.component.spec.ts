import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { StarRatingComponent } from 'app/assessment/manage/rating/star-rating/star-rating.component';

describe('StarRatingComponent', () => {
    setupTestBed({ zoneless: true });
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
            expect(component.starCount).toBe(5);
        });
    });

    // =========================================================================
    // Default Values
    // =========================================================================

    describe('default values', () => {
        it('should have default totalStars of 5', () => {
            expect(component.starCount).toBe(5);
        });

        it('should have default value of 0', () => {
            expect(component.value()).toBe(0);
        });

        it('should have default size of 24px', () => {
            expect(component.normalizedSize).toBe('24px');
        });

        it('should not be read-only by default', () => {
            expect(component.isReadOnly).toBe(false);
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
        it('should allow setting value programmatically via currentRating', () => {
            component.currentRating = 3;
            expect(component.currentRating).toBe(3);
        });

        it('should normalize undefined value to 0', () => {
            component.currentRating = undefined as unknown as number;
            expect(component.currentRating).toBe(0);
        });

        it('should normalize null value to 0', () => {
            component.currentRating = null as unknown as number;
            expect(component.currentRating).toBe(0);
        });

        it('should accept decimal values for half-star display', () => {
            component.currentRating = 3.5;
            expect(component.currentRating).toBe(3.5);
        });

        it('should accept value of 0', () => {
            component.currentRating = 0;
            expect(component.currentRating).toBe(0);
        });
    });

    // =========================================================================
    // Size Handling
    // =========================================================================

    describe('size handling', () => {
        it('should return size with px suffix when provided without', () => {
            fixture.componentRef.setInput('size', '32');
            fixture.detectChanges();

            expect(component.normalizedSize).toBe('32px');
        });

        it('should preserve px suffix when already provided', () => {
            fixture.componentRef.setInput('size', '48px');
            fixture.detectChanges();

            expect(component.normalizedSize).toBe('48px');
        });

        it('should default to 24px when size is empty', () => {
            fixture.componentRef.setInput('size', '');
            fixture.detectChanges();

            expect(component.normalizedSize).toBe('24px');
        });
    });

    // =========================================================================
    // TotalStars Handling
    // =========================================================================

    describe('totalStars handling', () => {
        it('should normalize negative totalStars to 5', () => {
            fixture.componentRef.setInput('totalStars', -1);
            fixture.detectChanges();

            expect(component.starCount).toBe(5);
        });

        it('should normalize zero totalStars to 5', () => {
            fixture.componentRef.setInput('totalStars', 0);
            fixture.detectChanges();

            expect(component.starCount).toBe(5);
        });

        it('should round decimal totalStars to nearest integer', () => {
            fixture.componentRef.setInput('totalStars', 3.7);
            fixture.detectChanges();

            expect(component.starCount).toBe(4);
        });

        it('should accept custom totalStars value', () => {
            fixture.componentRef.setInput('totalStars', 10);
            fixture.detectChanges();

            expect(component.starCount).toBe(10);
        });
    });

    // =========================================================================
    // Read-Only Mode
    // =========================================================================

    describe('read-only mode', () => {
        it('should respect readOnly input', () => {
            fixture.componentRef.setInput('readOnly', true);
            fixture.detectChanges();

            expect(component.isReadOnly).toBe(true);
        });

        it('should be editable when readOnly is false', () => {
            fixture.componentRef.setInput('readOnly', false);
            fixture.detectChanges();

            expect(component.isReadOnly).toBe(false);
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

        it('should emit rate event when star is clicked', () => {
            let emittedEvent: { oldValue: number; newValue: number } | undefined;
            component.rate.subscribe((event) => {
                emittedEvent = event;
            });

            const shadowRoot = fixture.nativeElement.shadowRoot;
            const stars = shadowRoot.querySelectorAll('[data-index]');
            expect(stars.length).toBe(5);

            // Click the 3rd star
            stars[2].click();
            fixture.detectChanges();

            expect(emittedEvent).toBeDefined();
            expect(emittedEvent!.oldValue).toBe(0);
            expect(emittedEvent!.newValue).toBe(3);
            expect(component.currentRating).toBe(3);
        });

        it('should not emit rate event when in read-only mode', () => {
            fixture.componentRef.setInput('readOnly', true);
            fixture.detectChanges();

            let emittedEvent: { oldValue: number; newValue: number } | undefined;
            component.rate.subscribe((event) => {
                emittedEvent = event;
            });

            const shadowRoot = fixture.nativeElement.shadowRoot;
            const stars = shadowRoot.querySelectorAll('[data-index]');

            // Attempt to click
            stars[2].click();
            fixture.detectChanges();

            expect(emittedEvent).toBeUndefined();
        });
    });

    // =========================================================================
    // Input Changes via setInput
    // =========================================================================

    describe('input changes', () => {
        it('should update currentRating when value input changes', () => {
            fixture.componentRef.setInput('value', 4);
            fixture.detectChanges();

            expect(component.currentRating).toBe(4);
        });

        it('should update starCount when totalStars input changes', () => {
            fixture.componentRef.setInput('totalStars', 7);
            fixture.detectChanges();

            expect(component.starCount).toBe(7);
        });
    });

    // =========================================================================
    // Star Hover Behavior
    // =========================================================================

    describe('star hover behavior', () => {
        it('should highlight stars on hover', () => {
            const shadowRoot = fixture.nativeElement.shadowRoot;
            const stars = shadowRoot.querySelectorAll('[data-index]');

            // Trigger mouseenter on 4th star
            const hoverEvent = new MouseEvent('mouseenter', { bubbles: true });
            stars[3].dispatchEvent(hoverEvent);
            fixture.detectChanges();

            // First 4 stars should have 'on' class (checked)
            for (let i = 0; i < 4; i++) {
                expect(stars[i].classList.contains('on')).toBe(true);
            }
            // 5th star should not have 'on' class
            expect(stars[4].classList.contains('on')).toBe(false);
        });

        it('should not highlight on hover when read-only', () => {
            fixture.componentRef.setInput('readOnly', true);
            fixture.detectChanges();

            const shadowRoot = fixture.nativeElement.shadowRoot;
            const stars = shadowRoot.querySelectorAll('[data-index]');

            const hoverEvent = new MouseEvent('mouseenter', { bubbles: true });
            stars[3].dispatchEvent(hoverEvent);
            fixture.detectChanges();

            // No stars should have 'on' class since initial value is 0
            for (let i = 0; i < 5; i++) {
                expect(stars[i].classList.contains('on')).toBe(false);
            }
        });

        it('should restore original state on mouse leave', () => {
            // Set initial rating to 2
            component.currentRating = 2;
            fixture.detectChanges();

            const shadowRoot = fixture.nativeElement.shadowRoot;
            const container = shadowRoot.querySelector('div');
            const stars = shadowRoot.querySelectorAll('[data-index]');

            // Hover on 4th star
            const hoverEvent = new MouseEvent('mouseenter', { bubbles: true });
            stars[3].dispatchEvent(hoverEvent);
            fixture.detectChanges();

            // Leave container
            const leaveEvent = new MouseEvent('mouseleave', { bubbles: true });
            container.dispatchEvent(leaveEvent);
            fixture.detectChanges();

            // Should be back to 2 stars highlighted
            expect(stars[0].classList.contains('on')).toBe(true);
            expect(stars[1].classList.contains('on')).toBe(true);
            expect(stars[2].classList.contains('on')).toBe(false);
        });
    });

    // =========================================================================
    // Star DOM Rendering
    // =========================================================================

    describe('star DOM rendering', () => {
        it('should create star elements in shadow DOM', () => {
            const shadowRoot = fixture.nativeElement.shadowRoot;
            const stars = shadowRoot.querySelectorAll('[data-index]');

            expect(stars.length).toBe(5);
            for (let i = 0; i < 5; i++) {
                expect(stars[i].getAttribute('data-index')).toBe((i + 1).toString());
            }
        });

        it('should apply star class to all elements', () => {
            const shadowRoot = fixture.nativeElement.shadowRoot;
            const stars = shadowRoot.querySelectorAll('[data-index]');

            for (let i = 0; i < 5; i++) {
                expect(stars[i].classList.contains('star')).toBe(true);
            }
        });

        it('should mark checked stars with on class', () => {
            component.currentRating = 3;
            fixture.detectChanges();

            const shadowRoot = fixture.nativeElement.shadowRoot;
            const stars = shadowRoot.querySelectorAll('[data-index]');

            expect(stars[0].classList.contains('on')).toBe(true);
            expect(stars[1].classList.contains('on')).toBe(true);
            expect(stars[2].classList.contains('on')).toBe(true);
            expect(stars[3].classList.contains('on')).toBe(false);
            expect(stars[4].classList.contains('on')).toBe(false);
        });

        it('should mark half star with half class', () => {
            component.currentRating = 2.5;
            fixture.detectChanges();

            const shadowRoot = fixture.nativeElement.shadowRoot;
            const stars = shadowRoot.querySelectorAll('[data-index]');

            expect(stars[0].classList.contains('on')).toBe(true);
            expect(stars[1].classList.contains('on')).toBe(true);
            expect(stars[2].classList.contains('half')).toBe(true);
            expect(stars[3].classList.contains('on')).toBe(false);
        });

        it('should update number of stars when totalStars changes', () => {
            fixture.componentRef.setInput('totalStars', 3);
            fixture.detectChanges();

            const shadowRoot = fixture.nativeElement.shadowRoot;
            const stars = shadowRoot.querySelectorAll('[data-index]');

            expect(stars.length).toBe(3);
        });
    });

    // =========================================================================
    // Cursor Styling
    // =========================================================================

    describe('cursor styling', () => {
        it('should set pointer cursor when not read-only', () => {
            fixture.componentRef.setInput('readOnly', false);
            fixture.detectChanges();

            const shadowRoot = fixture.nativeElement.shadowRoot;
            const container = shadowRoot.querySelector('div');

            expect(container.style.cursor).toBe('pointer');
        });

        it('should set default cursor when read-only', () => {
            fixture.componentRef.setInput('readOnly', true);
            fixture.detectChanges();

            const shadowRoot = fixture.nativeElement.shadowRoot;
            const container = shadowRoot.querySelector('div');

            expect(container.style.cursor).toBe('default');
        });
    });

    // =========================================================================
    // Edge Cases
    // =========================================================================

    describe('edge cases', () => {
        it('should handle clicking different stars', () => {
            const events: { oldValue: number; newValue: number }[] = [];
            component.rate.subscribe((event) => {
                events.push(event);
            });

            const shadowRoot = fixture.nativeElement.shadowRoot;
            const stars = shadowRoot.querySelectorAll('[data-index]');

            // Click 2nd star
            stars[1].click();
            fixture.detectChanges();

            expect(events.length).toBeGreaterThanOrEqual(1);
            expect(events[0]).toEqual({ oldValue: 0, newValue: 2 });
            expect(component.currentRating).toBe(2);
        });

        it('should handle value input of 0', () => {
            fixture.componentRef.setInput('value', 0);
            fixture.detectChanges();

            const shadowRoot = fixture.nativeElement.shadowRoot;
            const stars = shadowRoot.querySelectorAll('[data-index]');

            for (let i = 0; i < 5; i++) {
                expect(stars[i].classList.contains('on')).toBe(false);
            }
        });

        it('should handle clicking a star', () => {
            const events: { oldValue: number; newValue: number }[] = [];
            component.rate.subscribe((event) => {
                events.push(event);
            });

            const shadowRoot = fixture.nativeElement.shadowRoot;
            const stars = shadowRoot.querySelectorAll('[data-index]');

            stars[2].click();
            fixture.detectChanges();

            expect(events.length).toBe(1);
            expect(events[0]).toEqual({ oldValue: 0, newValue: 3 });
            expect(component.currentRating).toBe(3);
        });

        it('should correctly update rating with programmatic value change', () => {
            component.currentRating = 2;
            fixture.detectChanges();

            const shadowRoot = fixture.nativeElement.shadowRoot;
            let stars = shadowRoot.querySelectorAll('[data-index]');

            expect(stars[0].classList.contains('on')).toBe(true);
            expect(stars[1].classList.contains('on')).toBe(true);
            expect(stars[2].classList.contains('on')).toBe(false);

            component.currentRating = 4;
            fixture.detectChanges();

            // Re-query stars after value change as DOM may have been updated
            stars = shadowRoot.querySelectorAll('[data-index]');

            expect(stars[0].classList.contains('on')).toBe(true);
            expect(stars[1].classList.contains('on')).toBe(true);
            expect(stars[2].classList.contains('on')).toBe(true);
            expect(stars[3].classList.contains('on')).toBe(true);
            expect(stars[4].classList.contains('on')).toBe(false);
        });
    });

    // =========================================================================
    // Color Effects
    // =========================================================================

    describe('color effects', () => {
        it('should apply checkedColor to stars', () => {
            fixture.componentRef.setInput('checkedColor', 'gold');
            fixture.detectChanges();

            const shadowRoot = fixture.nativeElement.shadowRoot;
            const stars = shadowRoot.querySelectorAll('[data-index]');

            expect(stars[0].style.getPropertyValue('--checkedColor')).toBe('gold');
        });

        it('should apply uncheckedColor to stars', () => {
            fixture.componentRef.setInput('uncheckedColor', 'gray');
            fixture.detectChanges();

            const shadowRoot = fixture.nativeElement.shadowRoot;
            const stars = shadowRoot.querySelectorAll('[data-index]');

            expect(stars[0].style.getPropertyValue('--unCheckedColor')).toBe('gray');
        });
    });

    // =========================================================================
    // Size Effects
    // =========================================================================

    describe('size effects', () => {
        it('should apply size to star elements', () => {
            fixture.componentRef.setInput('size', '32px');
            fixture.detectChanges();

            const shadowRoot = fixture.nativeElement.shadowRoot;
            const stars = shadowRoot.querySelectorAll('[data-index]');

            expect(stars[0].style.getPropertyValue('--size')).toBe('32px');
        });
    });

    // =========================================================================
    // Title Attributes
    // =========================================================================

    describe('title attributes', () => {
        it('should set title on container with current rating', () => {
            component.currentRating = 3;
            fixture.detectChanges();

            const shadowRoot = fixture.nativeElement.shadowRoot;
            const container = shadowRoot.querySelector('div');

            expect(container.title).toBe('3');
        });

        it('should set title on each star with its index', () => {
            const shadowRoot = fixture.nativeElement.shadowRoot;
            const stars = shadowRoot.querySelectorAll('[data-index]');

            for (let i = 0; i < 5; i++) {
                expect(stars[i].title).toBe((i + 1).toString());
            }
        });

        it('should clear star titles when read-only', () => {
            fixture.componentRef.setInput('readOnly', true);
            fixture.detectChanges();

            const shadowRoot = fixture.nativeElement.shadowRoot;
            const stars = shadowRoot.querySelectorAll('[data-index]');

            for (let i = 0; i < 5; i++) {
                expect(stars[i].title).toBe('');
            }
        });
    });
});
