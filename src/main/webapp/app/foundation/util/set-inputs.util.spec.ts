import { describe, expect, it, vi } from 'vitest';
import { Component, input, model } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { setInputs } from 'app/foundation/util/set-inputs.util';

@Component({ selector: 'jhi-set-inputs-probe', template: '{{ label() }}|{{ count() }}|{{ checked() }}' })
class SetInputsProbeComponent {
    readonly label = input<string>();
    readonly count = input.required<number>();
    readonly checked = model(false);
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
        expect(fixture.nativeElement.textContent).toBe('Homework|3|true');
    });

    it('should leave inputs that are not passed as they are', () => {
        const fixture = create();
        setInputs(fixture.componentRef, { label: 'first', count: 1 });

        setInputs(fixture.componentRef, { count: 2 });

        expect(fixture.componentInstance.label()).toBe('first');
        expect(fixture.componentInstance.count()).toBe(2);
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

        consoleError.mockRestore();
    });
});
