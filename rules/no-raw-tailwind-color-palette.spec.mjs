import { describe, it } from 'vitest';
import rule from './no-raw-tailwind-color-palette.mjs';
import { createTemplateRuleTester } from './rule-tester.mjs';

const ruleTester = createTemplateRuleTester();

describe('no-raw-tailwind-color-palette', () => {
    it('flags raw palette / arbitrary color classes but allows brand-bound semantic tokens', () => {
        ruleTester.run('no-raw-tailwind-color-palette', rule, {
            valid: [
                // Brand/neutral semantic tokens (tailwindcss-primeui) and Artemis NAMED state tokens.
                { code: '<div class="text-primary"></div>' },
                { code: '<p-message styleClass="text-primary"></p-message>' },
                { code: '<div class="bg-primary"></div>' },
                { code: '<div class="text-surface-100"></div>' },
                { code: '<div class="text-muted-color"></div>' },
                { code: '<div class="text-state-danger"></div>' },
                { code: '<div class="bg-state-success"></div>' },
                { code: '<div class="border-state-warning"></div>' },
                // A realistic mix of allowed tokens in one attribute.
                { code: '<div class="text-primary bg-surface-100 text-state-warning"></div>' },
                // Bound class binding whose target is a semantic token.
                { code: '<div [class.text-primary]="active"></div>' },
                // Dynamic state colour via [style.*] referencing the raw var is the sanctioned escape hatch.
                { code: `<div [style.color]="ok ? 'var(--danger)' : undefined"></div>` },
                // Component-local arbitrary vars (not one of the four brand-state vars) stay allowed.
                { code: '<div class="text-(--artemis-alert-danger-color)"></div>' },
            ],
            invalid: [
                { code: '<div class="text-green-500"></div>', errors: [{ messageId: 'rawPalette', data: { cls: 'text-green-500' } }] },
                // PrimeNG styleClass passes colour utilities through to the host — scan it too.
                { code: '<p-message styleClass="text-green-500"></p-message>', errors: [{ messageId: 'rawPalette', data: { cls: 'text-green-500' } }] },
                { code: '<p-message [styleClass]="\'text-(--p-red-500)\'"></p-message>', errors: [{ messageId: 'primitivePalette', data: { cls: 'text-(--p-red-500)' } }] },
                { code: '<div class="bg-red-100"></div>', errors: [{ messageId: 'rawPalette', data: { cls: 'bg-red-100' } }] },
                // Arbitrary hex color value.
                { code: '<div class="bg-[#f00]"></div>', errors: [{ messageId: 'rawPalette', data: { cls: 'bg-[#f00]' } }] },
                // The older arbitrary brand-state form is superseded by the named state-* utilities.
                { code: '<div class="text-(--danger)"></div>', errors: [{ messageId: 'arbitraryStateToken', data: { cls: 'text-(--danger)' } }] },
                { code: '<div class="bg-(--success)"></div>', errors: [{ messageId: 'arbitraryStateToken', data: { cls: 'bg-(--success)' } }] },
                { code: `<div [ngClass]="cond ? 'text-(--warning)' : ''"></div>`, errors: [{ messageId: 'arbitraryStateToken', data: { cls: 'text-(--warning)' } }] },
                // [class.<palette>] — the banned token is the attribute name itself.
                { code: '<div [class.text-green-500]="warn"></div>', errors: [{ messageId: 'rawPalette', data: { cls: 'text-green-500' } }] },
                // Multiple raw palette classes in one attribute are each reported; a semantic token in
                // the same list is left untouched.
                {
                    code: '<div class="text-primary text-green-500 bg-red-100"></div>',
                    errors: [
                        { messageId: 'rawPalette', data: { cls: 'text-green-500' } },
                        { messageId: 'rawPalette', data: { cls: 'bg-red-100' } },
                    ],
                },
            ],
        });
    });

    it('flags PrimeNG primitive palette tokens used to express meaning', () => {
        ruleTester.run('no-raw-tailwind-color-palette', rule, {
            valid: [
                // Semantic PrimeNG tokens (no <colour>-<number> shape) are allowed.
                { code: '<div class="text-(--p-primary-color)"></div>' },
                { code: '<div class="text-(--p-text-muted-color)"></div>' },
            ],
            invalid: [
                // Primitive palette token as an arbitrary utility value.
                { code: '<div class="text-(--p-red-500)"></div>', errors: [{ messageId: 'primitivePalette', data: { cls: 'text-(--p-red-500)' } }] },
                { code: '<div class="bg-(--p-green-300)"></div>', errors: [{ messageId: 'primitivePalette', data: { cls: 'bg-(--p-green-300)' } }] },
                // Primitive palette token via var() inside an inline-style binding (the escape hatch).
                { code: `<div [style.color]="'var(--p-red-500)'"></div>`, errors: [{ messageId: 'primitivePalette', data: { cls: 'var(--p-red-500)' } }] },
            ],
        });
    });
});
