import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TumUiSeparatorComponent } from './tum-ui-separator.component';

describe('TumUiSeparatorComponent', () => {
    let fixture: ComponentFixture<TumUiSeparatorComponent>;
    let host: HTMLElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TumUiSeparatorComponent] }).compileComponents();
        fixture = TestBed.createComponent(TumUiSeparatorComponent);
        host = fixture.nativeElement as HTMLElement;
        fixture.detectChanges();
    });

    it('is decoration by default, so a rule between rows adds nothing to the accessibility tree', () => {
        expect(host.getAttribute('role')).toBe('none');
        expect(host.getAttribute('aria-orientation')).toBeNull();
        expect(host.getAttribute('data-slot')).toBe('separator');
        expect(host.getAttribute('data-orientation')).toBe('horizontal');
    });

    it('announces a boundary when the caller asks for one', () => {
        fixture.componentRef.setInput('decorative', false);
        fixture.detectChanges();
        expect(host.getAttribute('role')).toBe('separator');
    });

    it('states its axis only where it differs from the role default', () => {
        fixture.componentRef.setInput('orientation', 'vertical');
        fixture.detectChanges();
        expect(host.getAttribute('data-orientation')).toBe('vertical');
        expect(host.getAttribute('aria-orientation')).toBeNull();

        fixture.componentRef.setInput('decorative', false);
        fixture.detectChanges();
        expect(host.getAttribute('aria-orientation')).toBe('vertical');

        fixture.componentRef.setInput('orientation', 'horizontal');
        fixture.detectChanges();
        expect(host.getAttribute('aria-orientation')).toBeNull();
    });

    it('accepts the bare attribute form of decorative', () => {
        fixture.componentRef.setInput('decorative', '');
        fixture.detectChanges();
        expect(host.getAttribute('role')).toBe('none');
    });
});
