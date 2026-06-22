import { describe, it } from 'vitest';
import rule from './no-raw-tailwind-color-palette.mjs';
import { createTemplateRuleTester } from './rule-tester.mjs';

const ruleTester = createTemplateRuleTester();

describe('no-raw-tailwind-color-palette', () => {
    it('flags raw palette / arbitrary color classes but allows brand-bound semantic tokens', () => {
        ruleTester.run('no-raw-tailwind-color-palette', rule, {
            valid: [
                // Brand/neutral semantic tokens (tailwindcss-primeui) and Artemis state tokens in the arbitrary form.
                { code: '<div class="text-primary"></div>' },
                { code: '<div class="bg-primary"></div>' },
                { code: '<div class="text-surface-100"></div>' },
                { code: '<div class="text-muted-color"></div>' },
                { code: '<div class="text-(--danger)"></div>' },
                { code: '<div class="bg-(--success)"></div>' },
                // A realistic mix of allowed tokens in one attribute.
                { code: '<div class="text-primary bg-surface-100 text-(--warning)"></div>' },
                // Bound class binding whose target is a semantic token.
                { code: '<div [class.text-primary]="active"></div>' },
                // Dynamic state colour referencing a semantic token.
                { code: `<div [style.color]="ok ? 'var(--danger)' : undefined"></div>` },
            ],
            invalid: [
                { code: '<div class="text-green-500"></div>', errors: [{ messageId: 'rawPalette', data: { cls: 'text-green-500' } }] },
                { code: '<div class="bg-red-100"></div>', errors: [{ messageId: 'rawPalette', data: { cls: 'bg-red-100' } }] },
                // Arbitrary hex color value.
                { code: '<div class="bg-[#f00]"></div>', errors: [{ messageId: 'rawPalette', data: { cls: 'bg-[#f00]' } }] },
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
