import { describe, it } from 'vitest';
import rule from './no-component-ref-set-input.mjs';
import { createTypeScriptRuleTester } from './rule-tester.mjs';

const ruleTester = createTypeScriptRuleTester();

const error = { messageId: 'noSetInput' };

describe('no-component-ref-set-input', () => {
    it('accepts code that does not set inputs through the string based API', () => {
        ruleTester.run('no-component-ref-set-input', rule, {
            valid: [
                // The typed wrapper.
                { code: `setInputs(this.widgetRef, { thread, showLocationWarning });`, filename: 'src/main/webapp/app/exercise/review/manager.ts' },
                // A method that merely contains the word, or a differently named one.
                { code: `this.service.setInputs(a, b); this.ref.setInputValue('x', 1);`, filename: 'src/main/webapp/app/some.service.ts' },
                // A plain function that is not a method call.
                { code: `setInput('a', 1);`, filename: 'src/main/webapp/app/some.service.ts' },
                // Another computed member.
                { code: `this.ref['setInputs']('a', 1); this.ref[name]('a', 1); ref.setInput[method](ref, 'a', 1);`, filename: 'src/main/webapp/app/some.service.ts' },
                // Test code sets inputs on fixtures.
                { code: `fixture.componentRef.setInput('exercise', exercise);`, filename: 'src/main/webapp/app/exercise/foo.component.spec.ts' },
            ],
            invalid: [],
        });
    });

    it('rejects setInput on a component reference in production code', () => {
        ruleTester.run('no-component-ref-set-input', rule, {
            valid: [],
            invalid: [
                // A local variable.
                {
                    code: `const ref = createComponent(Widget, { hostElement }); ref.setInput('exercise', exercise);`,
                    filename: 'src/main/webapp/app/a/b.component.ts',
                    errors: [error],
                },
                // A member of the class.
                { code: `class A { run() { this.componentRef.setInput('detail', detail); } }`, filename: 'src/main/webapp/app/a/b.directive.ts', errors: [error] },
                // Optional chaining on a possibly missing reference.
                { code: `class A { run() { this.contentRef?.setInput('text', text); } }`, filename: 'packages/tum-aet-ui/src/lib/tooltip/tooltip.directive.ts', errors: [error] },
                // The bracket spellings reach the same member.
                { code: `this.ref['setInput']('a', 1);`, filename: 'src/main/webapp/app/some.service.ts', errors: [error] },
                { code: "this.ref[`setInput`]('a', 1);", filename: 'src/main/webapp/app/some.service.ts', errors: [error] },
                // call, apply and bind call it as well.
                {
                    code: `ref.setInput.call(ref, 'a', 1); ref.setInput.apply(ref, ['a', 1]); const set = ref.setInput.bind(ref);`,
                    filename: 'src/main/webapp/app/some.service.ts',
                    errors: [error, error, error],
                },
                // The call, apply and bind members can be spelled with brackets as well.
                {
                    code: "ref.setInput['call'](ref, 'a', 1); ref['setInput']['apply'](ref, ['a', 1]); const set = ref.setInput[`bind`](ref);",
                    filename: 'src/main/webapp/app/some.service.ts',
                    errors: [error, error, error],
                },
                // Every call is reported.
                {
                    code: `ref.setInput('a', 1); ref.setInput('b', 2);`,
                    filename: 'src/main/webapp/app/a/b.component.ts',
                    errors: [error, error],
                },
            ],
        });
    });
});
