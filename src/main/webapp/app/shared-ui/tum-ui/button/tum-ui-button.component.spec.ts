import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';

describe('TumUiButtonComponent', () => {
    let component: TumUiButtonComponent;
    let fixture: ComponentFixture<TumUiButtonComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TumUiButtonComponent, FontAwesomeTestingModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TumUiButtonComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function nativeButton(): HTMLButtonElement {
        return fixture.debugElement.query(By.css('button')).nativeElement;
    }

    it('renders a native button with the default solid-primary classes', () => {
        const className = nativeButton().className;
        expect(className).toContain('tum-ui-btn');
        expect(className).toContain('bg-primary');
        expect(className).toContain('text-base');
    });

    it('applies severity + size + the outlined variant', () => {
        fixture.componentRef.setInput('severity', 'danger');
        fixture.componentRef.setInput('size', 'small');
        fixture.componentRef.setInput('variant', 'outlined');
        fixture.detectChanges();
        const className = nativeButton().className;
        expect(className).toContain('bg-transparent');
        expect(className).toContain('text-state-danger');
        expect(className).toContain('border-state-danger');
        expect(className).toContain('text-sm');
    });

    it('applies the text variant (no border)', () => {
        fixture.componentRef.setInput('variant', 'text');
        fixture.detectChanges();
        const className = nativeButton().className;
        expect(className).toContain('bg-transparent');
        expect(className).toContain('border-transparent');
    });

    it('forwards the baseline aria-* inputs to the inner native button', () => {
        fixture.componentRef.setInput('ariaExpanded', true);
        fixture.componentRef.setInput('ariaControls', 'menu-1');
        fixture.componentRef.setInput('ariaDescribedBy', 'desc-1');
        fixture.detectChanges();
        const button = nativeButton();
        expect(button.getAttribute('aria-expanded')).toBe('true');
        expect(button.getAttribute('aria-controls')).toBe('menu-1');
        expect(button.getAttribute('aria-describedby')).toBe('desc-1');
        // Unset by default (no stray attributes).
        expect(button.hasAttribute('aria-pressed')).toBe(false);
    });

    it('reflects disabled and blocks the click output', () => {
        const emitSpy = vi.spyOn(component.clicked, 'emit');
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();
        const button = nativeButton();
        expect(button.disabled).toBe(true);
        button.click();
        expect(emitSpy).not.toHaveBeenCalled();
    });

    it('emits clicked when enabled', () => {
        const emitSpy = vi.spyOn(component.clicked, 'emit');
        nativeButton().click();
        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('sets the button type', () => {
        fixture.componentRef.setInput('type', 'submit');
        fixture.detectChanges();
        expect(nativeButton().getAttribute('type')).toBe('submit');
    });

    it('renders an icon when provided', () => {
        fixture.componentRef.setInput('icon', faCheck);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('fa-icon'))).toBeTruthy();
    });

    it('forwards ariaLabel to the inner native button so icon-only buttons are named', () => {
        // Icon-only button: the glyph carries no text, so the accessible name must come from aria-label
        // on the actual <button> element (not the host).
        fixture.componentRef.setInput('icon', faCheck);
        fixture.componentRef.setInput('ariaLabel', 'Confirm');
        fixture.detectChanges();
        expect(nativeButton().getAttribute('aria-label')).toBe('Confirm');
    });

    it('leaves aria-label unset when no accessible name is provided', () => {
        expect(nativeButton().hasAttribute('aria-label')).toBe(false);
    });

    it('applies the fully-rounded marker class when rounded', () => {
        expect(nativeButton().className).not.toContain('tum-ui-btn-rounded');
        fixture.componentRef.setInput('rounded', true);
        fixture.detectChanges();
        expect(nativeButton().className).toContain('tum-ui-btn-rounded');
    });

    it('shows a spinner, disables, and blocks clicks while loading', () => {
        const emitSpy = vi.spyOn(component.clicked, 'emit');
        fixture.componentRef.setInput('loading', true);
        fixture.detectChanges();
        const button = nativeButton();
        expect(button.disabled).toBe(true);
        expect(button.getAttribute('aria-busy')).toBe('true');
        expect(fixture.debugElement.query(By.css('fa-icon.animate-spin'))).toBeTruthy();
        button.click();
        expect(emitSpy).not.toHaveBeenCalled();
    });

    it('forwards styleClass onto the inner native button', () => {
        fixture.componentRef.setInput('styleClass', 'w-full');
        fixture.detectChanges();
        expect(nativeButton().className).toContain('w-full');
    });
});
