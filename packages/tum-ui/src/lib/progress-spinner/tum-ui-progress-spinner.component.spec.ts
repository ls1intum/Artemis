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

    it('reflects a custom stroke width and fill', () => {
        fixture.componentRef.setInput('strokeWidth', '4');
        fixture.componentRef.setInput('fill', 'transparent');
        fixture.detectChanges();
        expect(circle().getAttribute('stroke-width')).toBe('4');
        expect(circle().getAttribute('fill')).toBe('transparent');
    });

    it('reflects a custom animation duration onto the spin element', () => {
        fixture.componentRef.setInput('animationDuration', '1s');
        fixture.detectChanges();
        const spin = fixture.debugElement.query(By.css('.tum-ui-progress-spinner-spin')).nativeElement as SVGElement;
        expect(spin.style.animationDuration).toBe('1s');
    });
});
