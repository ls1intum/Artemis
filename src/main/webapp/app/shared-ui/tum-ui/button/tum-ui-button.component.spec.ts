import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';

describe('TumUiButtonComponent', () => {
    setupTestBed({ zoneless: true });

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

    it('applies severity + size + outlined variants', () => {
        fixture.componentRef.setInput('severity', 'danger');
        fixture.componentRef.setInput('size', 'small');
        fixture.componentRef.setInput('outlined', true);
        fixture.detectChanges();
        const className = nativeButton().className;
        expect(className).toContain('bg-transparent');
        expect(className).toContain('text-state-danger');
        expect(className).toContain('border-state-danger');
        expect(className).toContain('text-sm');
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
});
