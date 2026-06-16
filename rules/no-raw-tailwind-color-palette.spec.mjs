import { describe, it } from 'vitest';
import rule from './no-raw-tailwind-color-palette.mjs';
import { createTemplateRuleTester } from './rule-tester.mjs';

const ruleTester = createTemplateRuleTester();

describe('no-raw-tailwind-color-palette', () => {
    it('flags raw palette / arbitrary color classes but allows brand-bound semantic tokens', () => {
        ruleTester.run('no-raw-tailwind-color-palette', rule, {
            valid: [
                // Brand-bound semantic tokens — these are exactly what the rule steers reviewers toward.
                { code: '<div class="text-primary"></div>' },
                { code: '<div class="bg-primary"></div>' },
                { code: '<div class="text-surface-100"></div>' },
                { code: '<div class="text-muted-color"></div>' },
                { code: '<div class="text-success"></div>' },
                // A realistic mix of allowed tokens in one attribute.
                { code: '<div class="text-primary bg-surface-100 text-success"></div>' },
                // Bound class binding whose target is a semantic token.
                { code: '<div [class.text-primary]="active"></div>' },
                { code: '<div [ngClass]="{ \'text-success\': ok }"></div>' },
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
});
