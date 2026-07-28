import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { Component } from '@angular/core';
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

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function press(key: string): void {
        host.dispatchEvent(new KeyboardEvent('keydown', { key, cancelable: true }));
        fixture.detectChanges();
    }

    it('exposes the switch role and defaults to unchecked and focusable', () => {
        expect(host.getAttribute('role')).toBe('switch');
        expect(host.getAttribute('aria-checked')).toBe('false');
        expect(host.getAttribute('tabindex')).toBe('0');
        expect(host.className).toContain('tum-ui-toggle-switch');
        expect(host.className).toContain('bg-tum-ui-surface-300');
    });

    it('toggles on click, reflects aria-checked, turns the track primary, and emits the new value', () => {
        const changed = vi.fn();
        component.changed.subscribe(changed);

        host.click();
        fixture.detectChanges();

        expect(host.getAttribute('aria-checked')).toBe('true');
        expect(host.getAttribute('data-checked')).toBe('true');
        expect(host.className).toContain('bg-tum-ui-primary');
        expect(changed).toHaveBeenCalledWith(true);

        host.click();
        fixture.detectChanges();
        expect(host.getAttribute('aria-checked')).toBe('false');
        expect(changed).toHaveBeenLastCalledWith(false);
    });

    it.each([' ', 'Enter'])('toggles on the %s key and prevents default', (key) => {
        const changed = vi.fn();
        component.changed.subscribe(changed);
        const event = new KeyboardEvent('keydown', { key, cancelable: true });
        host.dispatchEvent(event);
        fixture.detectChanges();
        expect(host.getAttribute('aria-checked')).toBe('true');
        expect(changed).toHaveBeenCalledWith(true);
        expect(event.defaultPrevented).toBe(true);
    });

    it('ignores unrelated keys', () => {
        const changed = vi.fn();
        component.changed.subscribe(changed);
        press('a');
        expect(host.getAttribute('aria-checked')).toBe('false');
        expect(changed).not.toHaveBeenCalled();
    });

    it('reflects the value written through the ControlValueAccessor', () => {
        component.writeValue(true);
        fixture.detectChanges();
        expect(host.getAttribute('aria-checked')).toBe('true');
        component.writeValue(false);
        fixture.detectChanges();
        expect(host.getAttribute('aria-checked')).toBe('false');
    });

    it('invokes the registered onChange / onTouched callbacks when toggled', () => {
        const onChange = vi.fn();
        const onTouched = vi.fn();
        component.registerOnChange(onChange);
        component.registerOnTouched(onTouched);
        host.click();
        fixture.detectChanges();
        expect(onChange).toHaveBeenCalledWith(true);
        expect(onTouched).toHaveBeenCalled();
    });

    it('does not toggle or emit when disabled via the input', () => {
        const changed = vi.fn();
        component.changed.subscribe(changed);
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();
        expect(host.getAttribute('aria-disabled')).toBe('true');
        expect(host.getAttribute('tabindex')).toBe('-1');
        expect(host.className).toContain('opacity-60');

        host.click();
        press(' ');
        expect(host.getAttribute('aria-checked')).toBe('false');
        expect(changed).not.toHaveBeenCalled();
    });

    it('respects the reactive-forms disabled state', () => {
        component.setDisabledState(true);
        fixture.detectChanges();
        expect(host.getAttribute('aria-disabled')).toBe('true');
        host.click();
        expect(host.getAttribute('aria-checked')).toBe('false');
    });

    it('forwards inputId onto the host id', () => {
        fixture.componentRef.setInput('inputId', 'toggle-feature-x');
        fixture.detectChanges();
        expect(host.getAttribute('id')).toBe('toggle-feature-x');
    });
});

@Component({
    template: `<tum-ui-toggle-switch [formControl]="control" />`,
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
        const switchEl = fixture.nativeElement.querySelector('tum-ui-toggle-switch') as HTMLElement;

        switchEl.click();
        fixture.detectChanges();
        expect(fixture.componentInstance.control.value).toBe(true);

        fixture.componentInstance.control.disable();
        fixture.detectChanges();
        expect(switchEl.getAttribute('aria-disabled')).toBe('true');
    });
});
