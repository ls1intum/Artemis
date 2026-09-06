import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { Component, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TumUiToggleSwitchComponent } from './tum-ui-toggle-switch.component';

describe('TumUiToggleSwitchComponent', () => {
    let fixture: ComponentFixture<TumUiToggleSwitchComponent>;
    let component: TumUiToggleSwitchComponent;
    let host: HTMLElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiToggleSwitchComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiToggleSwitchComponent);
        component = fixture.componentInstance;
        host = fixture.nativeElement as HTMLElement;
        fixture.detectChanges();
    });

    function input(): HTMLInputElement {
        return host.querySelector('input[type="checkbox"]')!;
    }

    it('keeps its identity class while the state classes change around it', () => {
        // The identity class is static and the state classes are bound, so this is also the check that Angular
        // merges the two rather than letting the binding replace the static one.
        expect(host.classList.contains('tum-ui-toggle-switch')).toBe(true);
        expect(host.classList.contains('tum:bg-control-border')).toBe(true);

        component.writeValue(true);
        fixture.detectChanges();

        expect(host.classList.contains('tum-ui-toggle-switch')).toBe(true);
        expect(host.classList.contains('tum:bg-primary')).toBe(true);
        expect(host.classList.contains('tum:bg-control-border')).toBe(false);
    });

    it('uses a native checkbox with switch semantics', () => {
        expect(input().getAttribute('role')).toBe('switch');
        expect(input().checked).toBe(false);
        expect(input().disabled).toBe(false);
        expect(host.getAttribute('role')).toBeNull();
    });

    it('toggles on native input activation and emits the new value', () => {
        const changed = vi.fn();
        component.changed.subscribe(changed);

        input().click();
        fixture.detectChanges();

        expect(input().checked).toBe(true);
        expect(host.getAttribute('data-checked')).toBe('true');
        expect(changed).toHaveBeenCalledWith(true);

        input().click();
        fixture.detectChanges();
        expect(input().checked).toBe(false);
        expect(changed).toHaveBeenLastCalledWith(false);
    });

    it('reflects the value written through the ControlValueAccessor', () => {
        component.writeValue(true);
        fixture.detectChanges();
        expect(input().checked).toBe(true);
        component.writeValue(false);
        fixture.detectChanges();
        expect(input().checked).toBe(false);
    });

    it('invokes the registered onChange / onTouched callbacks when toggled', () => {
        const onChange = vi.fn();
        const onTouched = vi.fn();
        component.registerOnChange(onChange);
        component.registerOnTouched(onTouched);
        input().click();
        fixture.detectChanges();
        expect(onChange).toHaveBeenCalledWith(true);
        expect(onTouched).toHaveBeenCalled();
    });

    it('does not toggle or emit when disabled via the input', () => {
        const changed = vi.fn();
        component.changed.subscribe(changed);
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();
        expect(input().disabled).toBe(true);
        expect(host.getAttribute('data-disabled')).toBe('true');

        input().click();
        expect(input().checked).toBe(false);
        expect(changed).not.toHaveBeenCalled();
    });

    it('respects the reactive-forms disabled state', () => {
        component.setDisabledState(true);
        fixture.detectChanges();
        expect(input().disabled).toBe(true);
        input().click();
        expect(input().checked).toBe(false);
    });

    it('forwards id and accessible-name attributes to the native input', () => {
        fixture.componentRef.setInput('inputId', 'toggle-feature-x');
        fixture.componentRef.setInput('ariaLabel', 'Feature X');
        fixture.componentRef.setInput('ariaLabelledBy', 'feature-x-label');
        fixture.detectChanges();
        expect(input().id).toBe('toggle-feature-x');
        expect(input().getAttribute('aria-label')).toBe('Feature X');
        expect(input().getAttribute('aria-labelledby')).toBe('feature-x-label');
    });
});

@Component({
    template: `
        <label id="notifications-label" for="notifications">Email notifications</label>
        <tum-ui-toggle-switch inputId="notifications" aria-labelledby="notifications-label" [formControl]="control" />
    `,
    imports: [TumUiToggleSwitchComponent, ReactiveFormsModule],
})
class ReactiveHostComponent {
    readonly control = new FormControl(false);
}

describe('TumUiToggleSwitchComponent (reactive forms)', () => {
    it('writes the FormControl value on click and honors control.disable()', async () => {
        await TestBed.configureTestingModule({
            imports: [ReactiveHostComponent],
        }).compileComponents();
        const fixture = TestBed.createComponent(ReactiveHostComponent);
        fixture.detectChanges();
        const switchInput = fixture.nativeElement.querySelector('input[role="switch"]') as HTMLInputElement;
        const label = fixture.nativeElement.querySelector('label') as HTMLLabelElement;

        label.click();
        fixture.detectChanges();
        expect(label.htmlFor).toBe(switchInput.id);
        expect(switchInput.getAttribute('aria-labelledby')).toBe(label.id);
        expect(switchInput.checked).toBe(true);
        expect(fixture.componentInstance.control.value).toBe(true);

        fixture.componentInstance.control.disable();
        fixture.detectChanges();
        expect(switchInput.disabled).toBe(true);
    });
});

describe('TumUiToggleSwitchComponent accessible name', () => {
    @Component({
        imports: [TumUiToggleSwitchComponent],
        template: `
            <tum-ui-toggle-switch aria-label="Static name" />
            <tum-ui-toggle-switch [ariaLabel]="dynamicName()" />
        `,
    })
    class NamingHostComponent {
        readonly dynamicName = signal('Initial name');
    }

    it('names the inner switch from a static host attribute and from the ariaLabel input', () => {
        TestBed.configureTestingModule({ imports: [NamingHostComponent] });
        const fixture = TestBed.createComponent(NamingHostComponent);
        fixture.detectChanges();

        const switches = (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLInputElement>('input[role="switch"]');
        expect(switches[0].getAttribute('aria-label')).toBe('Static name');
        expect(switches[1].getAttribute('aria-label')).toBe('Initial name');

        fixture.componentInstance.dynamicName.set('Translated name');
        fixture.detectChanges();
        expect(switches[1].getAttribute('aria-label')).toBe('Translated name');
    });
});
