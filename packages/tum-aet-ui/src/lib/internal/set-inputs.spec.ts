import { Component, ComponentRef, input, model } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { setInputs } from './set-inputs';

@Component({ selector: 'tumaet-ui-set-inputs-probe', template: '{{ label() }}|{{ count() }}|{{ checked() }}' })
class SetInputsProbeComponent {
    readonly label = input<string>();
    readonly count = input.required<number>();
    readonly checked = model(false);
    // Set from a template as `[renamed]`, but named `shown` in the class.
    // eslint-disable-next-line @angular-eslint/no-input-rename -- the helper has to handle the aliased inputs that exist
    readonly shown = input('', { alias: 'renamed' });
    readonly notAnInput = 'plain member';
}

// This is the library's own copy of the application's setInputs, which the library cannot import. The spec mirrors the
// application's one, so that a change to only one of the two copies shows up as a failure here or there.
describe('setInputs', () => {
    const create = () => {
        const fixture = TestBed.createComponent(SetInputsProbeComponent);
        fixture.componentRef.setInput('count', 0);
        return fixture;
    };

    it('should set optional, required and model inputs on the created component', () => {
        const fixture = create();

        setInputs(fixture.componentRef, { label: 'Homework', count: 3, checked: true });
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toBe('Homework|3|true');
    });

    it('should leave inputs that are not passed as they are', () => {
        const fixture = create();
        setInputs(fixture.componentRef, { label: 'first', count: 1 });

        setInputs(fixture.componentRef, { count: 2 });

        expect(fixture.componentInstance.label()).toBe('first');
        expect(fixture.componentInstance.count()).toBe(2);
    });

    it('should set an input that is declared with an alias', () => {
        const fixture = create();
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

        setInputs(fixture.componentRef, { shown: 'by member name' });

        expect(fixture.componentInstance.shown()).toBe('by member name');
        expect(consoleError).not.toHaveBeenCalled();
        consoleError.mockRestore();
    });

    it('should set the inputs of a ref that does not know its component type', () => {
        const setInput = vi.fn();
        const ref = { setInput } as unknown as ComponentRef<SetInputsProbeComponent>;

        setInputs(ref, { label: 'mocked', count: 5 });

        expect(setInput.mock.calls).toEqual([
            ['label', 'mocked'],
            ['count', 5],
        ]);
    });

    it('should reject at compile time what the string based API cannot', () => {
        const fixture = create();
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
        const storedWithExtraKey = { count: 1, label: 'x', notAnInput: 'extra' };
        const storedWithOptionalKey: { count?: number } = { count: undefined };

        // @ts-expect-error a misspelled input name
        setInputs(fixture.componentRef, { lable: 'x' });
        // @ts-expect-error a value of the wrong type
        setInputs(fixture.componentRef, { count: 'three' });
        // @ts-expect-error a member that is not an input
        setInputs(fixture.componentRef, { notAnInput: 'x' });
        // @ts-expect-error undefined for a required input of type number
        setInputs(fixture.componentRef, { count: undefined });
        // @ts-expect-error an object in a variable that has all inputs and one more property
        setInputs(fixture.componentRef, storedWithExtraKey);
        // @ts-expect-error an object in a variable whose optional key may be undefined for a required input
        setInputs(fixture.componentRef, storedWithOptionalKey);

        consoleError.mockRestore();
    });
});
