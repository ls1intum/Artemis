import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { TumAetUiProgressSpinnerComponent } from './tumaet-ui-progress-spinner.component';

describe('TumAetUiProgressSpinnerComponent', () => {
    let fixture: ComponentFixture<TumAetUiProgressSpinnerComponent>;
    let host: HTMLElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumAetUiProgressSpinnerComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(TumAetUiProgressSpinnerComponent);
        host = fixture.nativeElement as HTMLElement;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function circle(): SVGCircleElement {
        return fixture.debugElement.query(By.css('.tumaet-ui-progress-spinner-circle')).nativeElement;
    }

    it('exposes a status role and busy state for assistive tech', () => {
        expect(host.getAttribute('role')).toBe('status');
        expect(host.getAttribute('aria-busy')).toBe('true');
    });

    it('renders the rotating svg with the animated circle', () => {
        expect(fixture.debugElement.query(By.css('svg.tumaet-ui-progress-spinner-spin'))).not.toBeNull();
        expect(circle().getAttribute('stroke-width')).toBe('2');
        expect(circle().getAttribute('fill')).toBe('none');
    });

    it('reflects the aria label', () => {
        fixture.componentRef.setInput('ariaLabel', 'Loading competencies');
        fixture.detectChanges();
        expect(host.getAttribute('aria-label')).toBe('Loading competencies');
    });
});
