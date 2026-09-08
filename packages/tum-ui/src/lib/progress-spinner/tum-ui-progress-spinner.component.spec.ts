import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { TumUiProgressSpinnerComponent } from './tum-ui-progress-spinner.component';

describe('TumUiProgressSpinnerComponent', () => {
    let fixture: ComponentFixture<TumUiProgressSpinnerComponent>;
    let host: HTMLElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiProgressSpinnerComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiProgressSpinnerComponent);
        host = fixture.nativeElement as HTMLElement;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function circle(): SVGCircleElement {
        return fixture.debugElement.query(By.css('.tum-ui-progress-spinner-circle')).nativeElement;
    }

    it('exposes a status role and busy state for assistive tech', () => {
        expect(host.getAttribute('role')).toBe('status');
        expect(host.getAttribute('aria-busy')).toBe('true');
    });

    it('renders the rotating svg with the animated circle', () => {
        expect(fixture.debugElement.query(By.css('svg.tum-ui-progress-spinner-spin'))).not.toBeNull();
        expect(circle().getAttribute('stroke-width')).toBe('2');
        expect(circle().getAttribute('fill')).toBe('none');
    });

    it('reflects the aria label', () => {
        fixture.componentRef.setInput('ariaLabel', 'Loading competencies');
        fixture.detectChanges();
        expect(host.getAttribute('aria-label')).toBe('Loading competencies');
    });

    it('can be sized, instead of every consumer inlining its own spinner around a fixed square', () => {
        expect(host.getAttribute('data-slot')).toBe('progress-spinner');
        expect(host.getAttribute('data-size')).toBe('large');

        fixture.componentRef.setInput('size', 'small');
        fixture.detectChanges();
        expect(host.getAttribute('data-size')).toBe('small');
        expect(host.classList).toContain('tum-ui-progress-spinner-small');
    });

    it('carries a static fallback for reduced motion rather than a frozen arc', () => {
        // `animation: none` on the arc leaves a three-quarter stroke that reads as a rendering fault. The static
        // ring is in the markup unconditionally and the stylesheet swaps between the two.
        expect(fixture.debugElement.query(By.css('.tum-ui-progress-spinner-static'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('svg')).nativeElement.getAttribute('aria-hidden')).toBe('true');
    });
});
