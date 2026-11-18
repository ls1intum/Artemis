import { describe, it } from 'vitest';
import rule from './no-primeng-component-classes.mjs';
import { createTemplateRuleTester } from './rule-tester.mjs';

const ruleTester = createTemplateRuleTester();

describe('no-primeng-component-classes', () => {
    it('forbids hand-written PrimeNG root classes but allows modifiers and real components', () => {
        ruleTester.run('no-primeng-component-classes', rule, {
            valid: [
                // Real PrimeNG components — the directive/element applies its own root class.
                { code: '<button pButton severity="danger">Delete</button>' },
                { code: '<input pInputText name="x" />' },
                { code: '<p-tag severity="success" />' },
                // Modifier classes on a real component are not flagged (only the bare root is).
                { code: '<button pButton class="p-button-secondary p-button-sm">Save</button>' },
                { code: '<input pInputText class="p-inputtext-sm" />' },
                // Non-root PrimeNG helper classes are not roots and stay allowed.
                { code: '<div class="p-fluid"></div>' },
                { code: '<span class="p-error">required</span>' },
                // A custom element styled with Tailwind utilities + tokens (the sanctioned alternative).
                { code: '<div class="rounded-md border px-3 py-2"></div>' },
                { code: '<p-message styleClass="flex gap-2 whitespace-nowrap"></p-message>' },
                // Substring guard: a custom class that merely contains a root name as a fragment is not matched.
                { code: '<div class="my-p-button-wrapper"></div>' },
                // Component INPUTS whose bound value happens to be a root token are not class bindings — the
                // `class.` key gate (rule) must leave them alone (mirrors the [close]/[card] case in the
                // no-bootstrap-classes spec).
                { code: `<my-widget [variant]="'p-button'"></my-widget>` },
                { code: `<div [attr.role]="'p-tag'"></div>` },
            ],
            invalid: [
                // Bare root class on a non-component element — the lazy-CSS footgun.
                { code: '<button class="p-button">Delete</button>', errors: [{ messageId: 'handPaintedRoot', data: { cls: 'p-button' } }] },
                // A root class hand-painted via PrimeNG styleClass hits the same lazy-CSS footgun.
                { code: '<p-message styleClass="p-button"></p-message>', errors: [{ messageId: 'handPaintedRoot', data: { cls: 'p-button' } }] },
                // Component-specific `*StyleClass` inputs pass classes through the same way — scan them too.
                { code: '<p-tree contentStyleClass="p-button"></p-tree>', errors: [{ messageId: 'handPaintedRoot', data: { cls: 'p-button' } }] },
                { code: '<div class="competency-selector p-inputtext p-2"></div>', errors: [{ messageId: 'handPaintedRoot', data: { cls: 'p-inputtext' } }] },
                // Root via [class.<root>] binding (root is the attribute name).
                { code: '<div [class.p-tag]="active"></div>', errors: [{ messageId: 'handPaintedRoot', data: { cls: 'p-tag' } }] },
                // Root inside a bound [ngClass] expression source.
                { code: `<div [ngClass]="ok ? 'p-message' : ''"></div>`, errors: [{ messageId: 'handPaintedRoot', data: { cls: 'p-message' } }] },
            ],
        });
    });
});
