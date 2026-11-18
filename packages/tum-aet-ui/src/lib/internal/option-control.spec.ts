import { Directive, inputBinding, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { OptionControl } from './option-control';

@Directive({ selector: '[optionControlProbe]' })
class OptionControlProbe extends OptionControl {
    text(option: unknown): string {
        return this.label(option);
    }
    value(option: unknown): unknown {
        return this.resolveValue(option);
    }
}

describe('OptionControl', () => {
    it('preserves primitive values and renders only scalar labels', () => {
        const fixture = TestBed.createDirective(OptionControlProbe, { tagName: 'div' });
        fixture.detectChanges();
        const control = fixture.directiveInstance;
        for (const value of ['hello', 0, false, 12n]) {
            expect(control.text(value)).toBe(String(value));
            expect(control.value(value)).toBe(value);
        }
        for (const value of [undefined, null, {}, ['nested'], () => 'function']) {
            expect(control.text(value)).toBe('');
            expect(control.value(value)).toBe(value);
        }
    });

    it('reacts to label and value field changes without copying object options', () => {
        const labelKey = signal('name');
        const valueKey = signal<string | undefined>('id');
        const fixture = TestBed.createDirective(OptionControlProbe, {
            tagName: 'div',
            bindings: [inputBinding('optionLabel', labelKey), inputBinding('optionValue', valueKey)],
        });
        fixture.detectChanges();
        const control = fixture.directiveInstance;
        const option = { name: 'Alice', enabled: false, id: 0 };
        expect(control.text(option)).toBe('Alice');
        expect(control.value(option)).toBe(0);
        labelKey.set('enabled');
        valueKey.set(undefined);
        fixture.detectChanges();
        expect(control.text(option)).toBe('false');
        expect(control.value(option)).toBe(option);
        expect(control.text(42)).toBe('42');
        expect(control.text(null)).toBe('');
    });

    it('inherits boolean input coercion used by both listbox controls', () => {
        const fixture = TestBed.createDirective(OptionControlProbe, {
            tagName: 'div',
            bindings: [inputBinding('disabled', () => ''), inputBinding('required', () => 'false')],
        });
        fixture.detectChanges();
        expect(fixture.directiveInstance.disabled()).toBe(true);
        expect(fixture.directiveInstance.required()).toBe(false);
    });
});
