import { describe, it } from 'vitest';
import rule from './no-ambiguous-spacing-utility.mjs';
import { createTemplateRuleTester } from './rule-tester.mjs';

const ruleTester = createTemplateRuleTester();

// What the rule must say per step of the scale, written out here rather than derived from the rule: what Bootstrap
// and Tailwind render, and the two spellings that remove the ambiguity.
const SCALE = {
    3: { bootstrap: '1rem (16px)', tailwind: '0.75rem (12px)', pinnedStep: '4!', tailwindStep: '3!' },
    4: { bootstrap: '1.5rem (24px)', tailwind: '1rem (16px)', pinnedStep: '6!', tailwindStep: '4!' },
    5: { bootstrap: '3rem (48px)', tailwind: '1.25rem (20px)', pinnedStep: '12!', tailwindStep: '5!' },
};

const PREFIXES = ['m', 'mt', 'mb', 'ms', 'me', 'mx', 'my', 'p', 'pt', 'pb', 'ps', 'pe', 'px', 'py', 'gap'];

// One error for `token` at the `@@` marker of `template`, with its two suggestions.
function ambiguous(template, token, position = {}) {
    const [, prefix, step] = /^(.+)-([345])$/.exec(token);
    const { bootstrap, tailwind, pinnedStep, tailwindStep } = SCALE[step];
    const pinned = `${prefix}-${pinnedStep}`;
    const twValue = `${prefix}-${tailwindStep}`;
    return {
        code: template.replace('@@', token),
        errors: [
            {
                messageId: 'ambiguousSpacing',
                data: { cls: token, bootstrap, tailwind, pinned, twValue },
                ...position,
                suggestions: [
                    { messageId: 'pinCurrentValue', data: { bootstrap, replacement: pinned }, output: template.replace('@@', pinned) },
                    { messageId: 'useTailwindValue', data: { tailwind, replacement: twValue }, output: template.replace('@@', twValue) },
                ],
            },
        ],
    };
}

describe('no-ambiguous-spacing-utility', () => {
    it('leaves the unambiguous spellings of a spacing utility alone', () => {
        ruleTester.run('no-ambiguous-spacing-utility', rule, {
            valid: [
                // 0 to 2 are the same in Bootstrap and Tailwind.
                { code: '<div class="m-0 m-1 m-2 mt-0 mb-1 ms-2 me-1 mx-2 my-0 p-1 pt-2 pb-0 ps-1 pe-2 px-1 py-2 gap-0 gap-1 gap-2"></div>' },
                // Bootstrap 5 has no ml/mr/pl/pr, so these are Tailwind only whatever the step.
                { code: '<div class="ml-3 mr-4 pl-5 pr-3 ml-4 mr-5 pl-3 pr-4"></div>' },
                // Above 5 only Tailwind has the class.
                { code: '<div class="mb-6 mt-8 p-10 px-12 gap-6 gap-8 my-16 pe-20"></div>' },
                // The Tailwind important modifier names the intent and wins over Bootstrap.
                { code: '<div class="mb-3! mt-4! p-5! gap-3! px-4! ms-5!"></div>' },
                // Variants are Tailwind only; Bootstrap has no `md:mb-3`.
                { code: '<div class="md:mb-3 hover:p-4 dark:gap-5 sm:px-3 lg:mt-4! focus-within:mb-5"></div>' },
                // Arbitrary values, fractions and the half steps are Tailwind only.
                { code: '<div class="mb-[3px] p-[1rem] gap-[--space] mb-3.5 px-4.5 mt-px"></div>' },
                // A negative utility is spelled `-mb-3` in Tailwind and `mb-n3` in Bootstrap.
                { code: '<div class="-mb-3 -mt-4 -mx-5 -ms-3 mb-n3"></div>' },
                // Auto is the same in both.
                { code: '<div class="m-auto mx-auto ms-auto mb-auto"></div>' },
                // Not the spacing utilities that merely look similar.
                { code: '<div class="gap-x-3 gap-y-4 gap-x-5 space-x-3 space-y-4 divide-x-3 scroll-m-3 scroll-pt-4 size-3 w-3 h-4 top-3 inset-4"></div>' },
                { code: '<div class="my-modal-3 mb-3x pb-3-x p-3p mb-4-5 gap-4-2 x-mb-3 mb--3 mb-"></div>' },
                { code: '<div class="m-3x"></div>' },
                // Another framework's class with the same prefix.
                { code: '<div class="pt-sans-4 mt-card-3"></div>' },
                // Not a class list.
                { code: '<div title="mb-3" id="p-4" aria-label="gap-5"></div>' },
                { code: '<p>use mb-3 or p-4</p>' },
                { code: '<my-cmp [p-3]="x" [mb-4]="y" [gap-5]="z"></my-cmp>' },
                { code: '<div [attr.data-spacing]="\'mb-3\'" [style.margin-bottom]="\'mb-3\'"></div>' },
                // Bound class names that are fine.
                { code: '<div [class.mb-6]="x" [class.mb-3!]="y" [class.md:mb-3]="z" [class.mb-2]="w"></div>' },
                { code: "<div [ngClass]=\"{ 'mb-6': x, 'mb-4!': y, 'gap-x-3': z, flex: w }\"></div>" },
                { code: '<div [class]="\'p-6 \' + extra"></div>' },
                { code: '<div [class]="`mb-12 ${extra}`"></div>' },
                { code: "<div [class]=\"cond ? 'mb-6' : 'mb-2'\"></div>" },
                // A name an expression computes cannot be judged.
                { code: '<div [class]="spacing"></div>' },
                { code: '<div [ngClass]="spacingClasses()"></div>' },
                // A literal glued to an interpolation is not a class of its own.
                { code: '<div class="{{ side }}mb-3 mb-3-{{ size }} a-{{ b }}"></div>' },
                { code: '<div [class]="`${side}mb-3`"></div>' },
                // Not judged, because the class name is not in the template text: a literal inside an interpolation of a class list, a plain
                // ngClass attribute and the case body of an ICU expression.
                { code: '<div class="{{ cond ? \'mb-3\' : \'mt-4\' }}" ngClass="p-5"></div>' },
                { code: '<span>{count, plural, =1 {<b class="mb-3"></b>} other {<b class="mt-4"></b>}}</span>' },
                // PrimeNG class inputs with the unambiguous spellings.
                { code: '<p-message styleClass="mb-6 p-2"></p-message>' },
                { code: '<p-button [styleClass]="\'mb-3! \' + extra"></p-button>' },
                // The same tokens in an attribute that does not take classes.
                { code: '<p-message styleClassName="mb-3" [styleClassName]="\'p-4\'"></p-message>' },
                { code: '<div [innerHTML]="\'mb-3\'"></div>' },
            ],
            invalid: [
                // Every prefix at every ambiguous step, in a static class list.
                ...PREFIXES.flatMap((prefix) => [3, 4, 5].map((step) => ambiguous('<div class="@@"></div>', `${prefix}-${step}`))),
                // The report points at the token, not at the attribute.
                ambiguous('<div class="flex @@ gap-2"></div>', 'mb-3', { line: 1, column: 18, endLine: 1, endColumn: 22 }),
                ambiguous('<div class="flex\n    gap-2\n    @@"></div>', 'px-4', { line: 3, column: 5, endLine: 3, endColumn: 9 }),
                ambiguous("<div class='@@'></div>", 'mt-3'),
                ambiguous('<div class="flex   @@\t\tgap-2"></div>', 'p-5'),
                // The token is reported once however many valid ones surround it.
                ambiguous('<div class="flex items-center gap-2 md:mb-3 @@ mt-2 mb-6 px-3!"></div>', 'my-4'),
                // [class.<token>]: the token is the attribute name.
                ambiguous('<div [class.@@]="isLast"></div>', 'mb-3'),
                ambiguous('<div class="flex" [class.@@]="i + 1 < items.length && a !== b"></div>', 'mb-4'),
                // [ngClass] in its quoted and its unquoted-key forms.
                ambiguous('<div [ngClass]="{ \'@@\': x }"></div>', 'mb-3'),
                ambiguous("<div [ngClass]=\"{ 'flex': y, '@@': x }\"></div>", 'p-5'),
                ambiguous("<div [ngClass]=\"['flex', '@@']\"></div>", 'gap-3'),
                ambiguous("<div [ngClass]=\"cond ? '@@' : ''\"></div>", 'mt-4'),
                ambiguous('<div [ngClass]="{ &quot;flex&quot;: y, \'@@ flex\': x }"></div>', 'me-3'),
                ambiguous('<div [ngClass]="{ flex: y, \'@@\': x }"></div>', 'ms-4'),
                // [class] as a string concatenation and as a template literal.
                ambiguous('<div [class]="\'@@ \' + extra"></div>', 'p-3'),
                ambiguous('<div [class]="extra + \' flex @@\'"></div>', 'py-4'),
                ambiguous('<div [class]="`@@ ${extra}`"></div>', 'mb-3'),
                ambiguous('<div [class]="`flex ${a} @@ ${b}`"></div>', 'ps-5'),
                // A class list with interpolations.
                ambiguous('<div class="feedback--{{ color() }} mb-2 flex @@"></div>', 'px-3'),
                ambiguous('<div class="@@ {{ a }}{{ b }}"></div>', 'gap-4'),
                // An element with a structural directive is visited as the template and as the element, and is reported once.
                ambiguous('<div *ngIf="a" [class.@@]="x"></div>', 'mb-3'),
                ambiguous('<div *jhiHasAnyAuthority="\'ROLE_ADMIN\'" [ngClass]="{ \'@@\': x }"></div>', 'p-4'),
                // PrimeNG class inputs render onto the host.
                ambiguous('<p-message styleClass="@@"></p-message>', 'mb-3'),
                ambiguous('<p-button [styleClass]="\'@@ \' + extra"></p-button>', 'mt-5'),
                ambiguous('<p-tree contentStyleClass="flex @@"></p-tree>', 'p-4'),
                // Inside a nested template.
                ambiguous('@if (a) {\n    @for (i of items; track i) {\n        <li class="@@"></li>\n    }\n}', 'pb-3'),
                // Several in one attribute: each gets its own error and suggestions that change only that token.
                {
                    code: '<div class="mb-3 flex p-4"></div>',
                    errors: [
                        {
                            messageId: 'ambiguousSpacing',
                            data: { cls: 'mb-3', bootstrap: '1rem (16px)', tailwind: '0.75rem (12px)', pinned: 'mb-4!', twValue: 'mb-3!' },
                            suggestions: [
                                { messageId: 'pinCurrentValue', data: { bootstrap: '1rem (16px)', replacement: 'mb-4!' }, output: '<div class="mb-4! flex p-4"></div>' },
                                { messageId: 'useTailwindValue', data: { tailwind: '0.75rem (12px)', replacement: 'mb-3!' }, output: '<div class="mb-3! flex p-4"></div>' },
                            ],
                        },
                        {
                            messageId: 'ambiguousSpacing',
                            data: { cls: 'p-4', bootstrap: '1.5rem (24px)', tailwind: '1rem (16px)', pinned: 'p-6!', twValue: 'p-4!' },
                            suggestions: [
                                { messageId: 'pinCurrentValue', data: { bootstrap: '1.5rem (24px)', replacement: 'p-6!' }, output: '<div class="mb-3 flex p-6!"></div>' },
                                { messageId: 'useTailwindValue', data: { tailwind: '1rem (16px)', replacement: 'p-4!' }, output: '<div class="mb-3 flex p-4!"></div>' },
                            ],
                        },
                    ],
                },
                // The same token twice is reported twice and each suggestion rewrites its own occurrence.
                {
                    code: '<div class="mb-3" [class.mb-3]="x"></div>',
                    errors: [
                        {
                            messageId: 'ambiguousSpacing',
                            data: { cls: 'mb-3', bootstrap: '1rem (16px)', tailwind: '0.75rem (12px)', pinned: 'mb-4!', twValue: 'mb-3!' },
                            suggestions: [
                                { messageId: 'pinCurrentValue', data: { bootstrap: '1rem (16px)', replacement: 'mb-4!' }, output: '<div class="mb-4!" [class.mb-3]="x"></div>' },
                                { messageId: 'useTailwindValue', data: { tailwind: '0.75rem (12px)', replacement: 'mb-3!' }, output: '<div class="mb-3!" [class.mb-3]="x"></div>' },
                            ],
                        },
                        {
                            messageId: 'ambiguousSpacing',
                            data: { cls: 'mb-3', bootstrap: '1rem (16px)', tailwind: '0.75rem (12px)', pinned: 'mb-4!', twValue: 'mb-3!' },
                            suggestions: [
                                { messageId: 'pinCurrentValue', data: { bootstrap: '1rem (16px)', replacement: 'mb-4!' }, output: '<div class="mb-3" [class.mb-4!]="x"></div>' },
                                { messageId: 'useTailwindValue', data: { tailwind: '0.75rem (12px)', replacement: 'mb-3!' }, output: '<div class="mb-3" [class.mb-3!]="x"></div>' },
                            ],
                        },
                    ],
                },
            ],
        });
    });
});
