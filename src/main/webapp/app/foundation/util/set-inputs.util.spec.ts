import { describe, expect, it, vi } from 'vitest';
import { Component, input, model } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { setInputs } from 'app/foundation/util/set-inputs.util';

@Component({ selector: 'jhi-set-inputs-probe', template: '{{ label() }}|{{ count() }}|{{ checked() }}|{{ shown() }}' })
class SetInputsProbeComponent {
    readonly label = input<string>();
    readonly count = input.required<number>();
    readonly checked = model(false);
    // Set from a template as `[renamed]`, but named `shown` in the class.
    // eslint-disable-next-line @angular-eslint/no-input-rename -- the helper has to handle the aliased inputs that exist
    readonly shown = input('', { alias: 'renamed' });
    readonly notAnInput = 'plain member';
}

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

        expect(fixture.componentInstance.label()).toBe('Homework');
        expect(fixture.componentInstance.count()).toBe(3);
        expect(fixture.componentInstance.checked()).toBe(true);
        expect(fixture.nativeElement.textContent).toBe('Homework|3|true|');
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

    it('should accept undefined only for an input whose type includes it', () => {
        const fixture = create();
        setInputs(fixture.componentRef, { label: 'set' });

        setInputs(fixture.componentRef, { label: undefined });

        expect(fixture.componentInstance.label()).toBeUndefined();
    });

    it('should accept an object that is kept in a variable when all of its keys are inputs', () => {
        const fixture = create();
        const stored = { label: 'stored', count: 4 };

        setInputs(fixture.componentRef, stored);

        expect(fixture.componentInstance.label()).toBe('stored');
        expect(fixture.componentInstance.count()).toBe(4);
    });

    it('should reject at compile time what the string based API cannot', () => {
        const fixture = create();
        // Angular only logs an unknown input at runtime; the point here is that the calls below do not compile.
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

        // @ts-expect-error a misspelled input name
        setInputs(fixture.componentRef, { lable: 'x' });
        // @ts-expect-error a value of the wrong type
        setInputs(fixture.componentRef, { count: 'three' });
        // @ts-expect-error a member that is not an input
        setInputs(fixture.componentRef, { notAnInput: 'x' });
        // @ts-expect-error undefined for a required input of type number
        setInputs(fixture.componentRef, { count: undefined });
        // @ts-expect-error a value that may be undefined for a required input of type number
        setInputs(fixture.componentRef, { count: Math.random() > 2 ? 1 : undefined });
        // Excess properties are only reported for an object literal, so a stored object needs its own check.
        const storedWithExtraKey = { count: 1, label: 'x', notAnInput: 'extra' };
        // @ts-expect-error an object in a variable that has all inputs and one more property
        setInputs(fixture.componentRef, storedWithExtraKey);

        consoleError.mockRestore();
    });
});
