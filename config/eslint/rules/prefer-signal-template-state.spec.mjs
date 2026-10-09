import { describe, it } from 'vitest';
import rule from './prefer-signal-template-state.mjs';
import { createTypeScriptRuleTester } from './rule-tester.mjs';

const ruleTester = createTypeScriptRuleTester();

/** A component with one plain, reassigned field and the given inline template. */
function componentWith(template, field) {
    return `
        @Component({ selector: 'jhi-example', template: '${template}' })
        export class ExampleComponent {
            ${field} = 0;
            update() { this.${field} = this.${field} + 1; }
        }
    `;
}

describe('prefer-signal-template-state', () => {
    it('flags a plain, reassigned field that the template reads', () => {
        ruleTester.run('prefer-signal-template-state', rule, {
            valid: [
                // A signal is the required form.
                {
                    code: `
                        @Component({ selector: 'jhi-example', template: '{{ counter() }}' })
                        export class ExampleComponent {
                            counter = signal(0);
                            update() { this.counter.update((value) => value + 1); }
                        }
                    `,
                },
                // A field the template never reads cannot cause a render bug.
                { code: componentWith('{{ other }}', 'counter') },
            ],
            invalid: [{ code: componentWith('{{ counter }}', 'counter'), errors: [{ messageId: 'preferSignalTemplateState' }] }],
        });
    });

    it('ignores bindings inside a template comment', () => {
        ruleTester.run('prefer-signal-template-state', rule, {
            valid: [{ code: componentWith('<!-- {{ counter }} -->', 'counter') }],
            invalid: [
                // Only the commented-out occurrence is ignored; the live one still counts.
                { code: componentWith('<!-- {{ counter }} --> {{ counter }}', 'counter'), errors: [{ messageId: 'preferSignalTemplateState' }] },
            ],
        });
    });

    // Regression test for code-scanning alert js/incomplete-multi-character-sanitization: removing comments with a
    // single `replace` pass lets the removal reassemble a comment marker. `<!<!-- a -->-- x -->` becomes
    // `<!-- x -->` after one pass, so a binding inside it was still seen as live template code.
    describe('comment markers reassembled by removing an inner comment', () => {
        it('does not count a binding hidden in the reassembled comment as read', () => {
            ruleTester.run('prefer-signal-template-state', rule, {
                valid: [{ code: componentWith('<!<!-- a -->-- {{ counter }} -->', 'counter') }],
                invalid: [],
            });
        });

        it('does not let a two-way binding in the reassembled comment exempt the field', () => {
            ruleTester.run('prefer-signal-template-state', rule, {
                valid: [],
                invalid: [
                    {
                        code: componentWith('<!<!-- a -->-- [(value)]="counter" --> {{ counter }}', 'counter'),
                        errors: [{ messageId: 'preferSignalTemplateState' }],
                    },
                ],
            });
        });

        it('still honours a genuine two-way binding outside any comment', () => {
            ruleTester.run('prefer-signal-template-state', rule, {
                valid: [{ code: componentWith('<!-- a --> <input [(value)]="counter" />', 'counter') }],
                invalid: [],
            });
        });
    });
});
